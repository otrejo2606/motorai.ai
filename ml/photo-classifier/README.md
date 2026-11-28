# Clasificador del ángulo de las fotos (Ultralytics YOLO)

Servicio de inferencia y entrenamiento del modelo local que valida que cada foto del anuncio sea la toma pedida. Cambio OpenSpec `openspec/changes/add-clasificador-fotos-yolo/`. El backend ya lo llama con `HttpPhotoAngleClassifierAdapter` (design D3 de `add-captura-guiada-fotos`).

> **Licencia:** Ultralytics es AGPL-3.0. Se usa la licencia gratuita durante el desarrollo y se firmará la Enterprise antes del lanzamiento. Leer [LICENSE-NOTICE.md](LICENSE-NOTICE.md).

## Estructura

| Ruta | Qué es |
|---|---|
| `service/app.py` | API: `POST /v1/classify` y `GET /health` |
| `service/classifier.py` | interfaz del clasificador, filtrado de etiquetas y carga de YOLO |
| `service/imaging.py` | decodificación segura (JPEG, PNG, WebP; límite de píxeles) |
| `service/readings.py` | punto de extensión del OCR de VIN y odómetro (puerta 0.5) |
| `service/labels.py` | las 14 tomas más `OTHER`, iguales a las del backend |
| `service/config.py` | variables de entorno |
| `training/prepare_dataset.py` | arma `train/val/test/<TOMA>/` desde un CSV o carpetas |
| `training/train.py` | entrena (`YOLO(...).train`, tarea classify) |
| `training/evaluate.py` | top-1, matriz de confusión, umbrales del backend, latencia |
| `training/export.py` | exporta a ONNX para servir en CPU |
| `tests/` | pruebas con pytest y un clasificador falso (sin ultralytics) |
| `Dockerfile`, `deploy/` | imagen y docker compose para la VM de IA |

## Contrato

`POST /v1/classify`, `Content-Type: application/json`:

```json
{"expectedSlot": "FRONT", "mimeType": "image/jpeg", "imageBase64": "..."}
```

Respuesta 200:

```json
{"modelVersion": "angle-yolo11n-cls-2027.01",
 "predictions": [{"slot": "FRONT", "confidence": 0.93}, {"slot": "FRONT_LEFT", "confidence": 0.05}],
 "vin": null, "odometerKm": null}
```

- `predictions`: hasta `TOP_K` (5), de mayor a menor, solo con las 15 etiquetas conocidas y confianza entre 0 y 1.
- `expectedSlot` es contexto: el modelo predice por su cuenta. El servicio no lo registra.
- `vin` y `odometerKm` siempre `null` hasta el OCR (puerta 0.5).

| Estado | Cuándo | Código del backend |
|---|---|---|
| 400 | JSON mal formado, campos faltantes, base64 inválido, imagen dañada | `PHOTO_CLASSIFIER_IMAGE_UNREADABLE` |
| 415 | tipo distinto de JPEG, PNG o WebP, o bytes que no son del tipo declarado | `PHOTO_CLASSIFIER_IMAGE_UNREADABLE` |
| 422 | imagen con más píxeles que `MAX_IMAGE_PIXELS` | `PHOTO_CLASSIFIER_IMAGE_UNREADABLE` |
| 401 | `CLASSIFIER_TOKEN` configurado y el Bearer falta o no coincide | `PHOTO_CLASSIFIER_NOT_CONFIGURED` |
| 413 | cuerpo mayor que `MAX_BODY_BYTES` | `PHOTO_CLASSIFIER_UNAVAILABLE` |
| 500 | falla de la inferencia o modelo sin etiquetas conocidas | `PHOTO_CLASSIFIER_UNAVAILABLE` |
| 503 | no hay modelo cargado | `PHOTO_CLASSIFIER_UNAVAILABLE` |

`GET /health`: 200 `{"status": "UP", "modelLoaded": true, "modelVersion": "..."}` o 503 `{"status": "DOWN", "modelLoaded": false, "modelVersion": null}`.

## Variables del servicio

| Variable | Por defecto | Uso |
|---|---|---|
| `MODEL_PATH` | `/models/model.onnx` en la imagen | modelo `.pt` u `.onnx`, montado por volumen |
| `MODEL_VERSION` | nombre del archivo | versión en cada respuesta (hasta 60 caracteres) |
| `CLASSIFIER_TOKEN` | vacío | Bearer opcional, comparado en tiempo constante |
| `MAX_BODY_BYTES` | 8388608 (8 MiB) | tamaño máximo del cuerpo |
| `MAX_IMAGE_PIXELS` | 16777216 (4096 x 4096) | ancho por alto máximo |
| `TOP_K` | 5 | predicciones devueltas |
| `IMAGE_SIZE` | 224 | lado de entrada del modelo; igual que en el entrenamiento |
| `DEVICE` | `cpu` | dispositivo de Ultralytics |
| `LOG_LEVEL` | `INFO` | nivel de log |

El servicio nunca guarda ni registra la imagen, el base64 ni la toma enviada. Los logs llevan solo la latencia y el tipo de error.

## Dataset (puerta 0.1)

### De dónde salen las fotos

1. **Fotos de anuncios de Motoria** de vendedores con el consentimiento `MODEL_TRAINING` («Mejora de modelos de IA») vigente al momento de exportarlas. La base legal es ese consentimiento según la Ley 8968 (PRODHAB). Está **por verificar** con el asesor legal que su texto cubra este uso. Es la puerta 0.1. Si el vendedor retira el consentimiento, sus fotos salen del dataset siguiente.
2. **Fotos corregidas por el analista** en la bandeja «Ángulo de las fotos» (`add-captura-guiada-fotos`, D7), con el mismo consentimiento.
3. **Fotos propias o de aliados** (autolavados, talleres) con licencia o cesión por escrito.
4. **Nunca** fotos de terceros sin licencia (portales de la competencia, buscadores, redes sociales).

Antes de exportar: sin placas ni rostros legibles en lo que se conserve (difuminarlos o descartar la foto). `prepare_dataset.py` elimina EXIF y GPS al recodificar, pero no difumina.

### Cuántas por clase

| Fase | Mínimo de entrenamiento por clase | Validación y prueba |
|---|---|---|
| Prueba de humo | 50 | sin exigencia |
| Primer modelo candidato | **300** (valor por defecto de `--min-per-class`) | 15 % y 15 % |
| Producción | 1 000 o más en las esquinas y laterales (las más parecidas) | conjunto dorado de al menos 100 por clase |

`OTHER` necesita variedad: fotos en negro, pantallas, documentos, personas, otros objetos, calles sin vehículo.

### Guía de etiquetado

- La etiqueta parte de la toma declarada por el vendedor y la revisa una persona. El conjunto dorado (validación y prueba) lo revisan dos personas. Si discrepan, se descarta la foto.
- **Izquierda y derecha son las del vehículo** (como sentado en el asiento del conductor), igual que las siluetas del portal (`slotGuides`). `FRONT_LEFT` es la esquina delantera izquierda del vehículo.
- Exteriores: el vehículo debe ocupar la mayor parte del cuadro. Si se ve más un costado que el frente, es el costado.
- `INTERIOR_FRONT`: asientos delanteros y tablero. `INTERIOR_REAR`: asientos traseros. `DASHBOARD_ODOMETER`: el cuadro de instrumentos con el odómetro legible. `ENGINE`: compartimiento del motor abierto. `TIRES`: una llanta con su banda visible. `VIN`: la placa o grabado del VIN.
- `OTHER`: cualquier foto que no sea una de las 14 tomas.
- Una foto ambigua (entre dos tomas) no entra al conjunto dorado.
- **Separar por vehículo:** todas las fotos de un vehículo van en la misma división (`vehicle_id` del CSV), para que el modelo no se evalúe con el mismo auto que vio al entrenar.

### Formato de entrada

CSV con encabezado `path,slot,vehicle_id,split,source`:

- `path`: relativa a `--images-root`;
- `slot`: una de las 15 etiquetas;
- `vehicle_id`: id interno del vehículo, sin placa;
- `split`: vacío, o `test` o `val` para fijar el conjunto dorado;
- `source`: `listing`, `own` o `partner`.

También sirve una carpeta por etiqueta (`--from-folders`), con el prefijo `<vehículo>__` en el nombre del archivo para agrupar.

## Entrenar, evaluar y exportar

Lo corre el dueño o el equipo de datos en una máquina de entrenamiento, desde `ml/photo-classifier` y con Python 3.11. Con GPU, instalar antes la versión de `torch` que corresponda a su CUDA.

```
pip install -r requirements-train.txt
python -m training.prepare_dataset --labels-csv etiquetas.csv --images-root /datos/fotos --out /datos/angle-v1
python -m training.train --data /datos/angle-v1 --model yolo11n-cls.pt --epochs 60 --imgsz 224 --device 0
python -m training.evaluate --model runs/angle/yolo11n-cls/weights/best.pt --data /datos/angle-v1 --split test
python -m training.export --model runs/angle/yolo11n-cls/weights/best.pt --version angle-yolo11n-cls-2027.01
python -m training.evaluate --model models/angle-yolo11n-cls-2027.01.onnx --data /datos/angle-v1 --split test
```

- El volteo horizontal está apagado en `train.py`, porque cambia la toma.
- La telemetría de Ultralytics se apaga en cada script (`settings.update({"sync": False})`).
- La segunda evaluación, con el ONNX y `--device cpu`, mide la latencia en CPU. Debe repetirse en la VM de IA.

### Métricas mínimas para conectarlo (design D7)

Son una propuesta: las confirma el dueño en la puerta 0.2 de `add-clasificador-fotos-yolo` y se ajustan en `training/evaluate.py`.

| Métrica | Mínimo | Dónde se lee |
|---|---|---|
| Exactitud top-1 en exteriores (8 tomas) | ≥ 95 % | `report.json`, `top1.exterior` |
| Exactitud top-1 en detalle (interior, tablero, motor, llantas, VIN) | ≥ 90 % | `top1.detail` |
| Falsos `MISMATCH` del vendedor honesto con 0.80 y 0.90 | < 1 % | `honestSeller.MISMATCH_rate` |
| Latencia p95 por imagen en la VM de IA | < 300 ms | `latencyMs.p95` |

`meetsMinimums` resume las cuatro. Además, revisar `confusion_matrix.csv` (pares de esquinas y laterales), `wrongShot` (fotos equivocadas aceptadas por error) y `thresholdSweep`, por si conviene otro `accept-threshold` o `reject-threshold` en el backend.

## Desplegar en la VM de IA (puerta 0.3)

La VM de IA está en otro tenancy y se une a producción por WireGuard (10.90.0.0/24). El servicio escucha solo en la dirección de la VM dentro del túnel.

1. Copiar el `.onnx` exportado a la carpeta de modelos de la VM (`MODELS_DIR`).
2. En `deploy/`, copiar `photo-classifier.env.example` a `photo-classifier.env` y completarlo.
3. Ejecutar `docker compose --env-file photo-classifier.env up -d --build`.
4. Desde producción, por el túnel, `GET http://<WG_BIND_ADDRESS>:8080/health` debe responder `modelLoaded: true`.
5. En el backend, con los nombres reales de `backend/src/main/resources/application.properties`:

| Variable de entorno | Propiedad | Valor |
|---|---|---|
| `MOTORIA_PHOTO_CLASSIFIER_PROVIDER` | `motoria.media.angle-check.provider` | `http` |
| `MOTORIA_PHOTO_CLASSIFIER_URL` | `motoria.media.angle-check.url` | `http://<WG_BIND_ADDRESS>:8080/v1/classify` (la URL completa: el adaptador hace `POST` a ella tal cual) |
| `MOTORIA_PHOTO_CLASSIFIER_TOKEN` | `motoria.media.angle-check.token` | el mismo `CLASSIFIER_TOKEN`, si se configuró |
| `MOTORIA_PHOTO_ANGLE_CHECK_ENABLED` | `motoria.media.angle-check.enabled` | `true` |

`motoria.media.angle-check.timeout` (PT5S), los umbrales (`accept-threshold` 0.80, `reject-threshold` 0.90) y `max-side` (1024) no tienen variable de entorno: se cambian en `application.properties`.

Una sola réplica con `--workers 1`: el modelo se carga una vez y las inferencias van de a una. Para más capacidad, más CPU o más contenedores detrás de un balanceador en la misma red privada.

## Pruebas (las corre el CI o el dueño)

```
pip install -r requirements-dev.txt
pytest
```

No necesitan Ultralytics ni torch: usan un clasificador falso (`tests/helpers.py`).
