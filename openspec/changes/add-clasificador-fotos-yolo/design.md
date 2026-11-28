# Design

Rutas relativas a `ml/photo-classifier/` salvo que se indique otra.

## D1. Contrato: el mismo del adaptador Java

El servicio implementa sin cambios el contrato de design D3 de `add-captura-guiada-fotos`, que lee `HttpPhotoAngleClassifierAdapter` (`parse`, `requestBody`).

- **Solicitud:** `POST /v1/classify` con `{"expectedSlot", "mimeType", "imageBase64"}`. El backend manda un JPEG sin EXIF, rotado y reducido al lado mayor de `motoria.media.angle-check.max-side` (1024).
- **Respuesta 200:** `{"modelVersion", "predictions": [{"slot", "confidence"}], "vin", "odometerKm"}`:
  - `predictions` trae hasta `TOP_K` (5), de mayor a menor confianza, solo con las 14 tomas u `OTHER` y confianza finita entre 0 y 1 (`service/classifier.py`, `rank`). El adaptador tomaría igual la mejor y descartaría el resto, pero filtrar en el servicio evita que una etiqueta extra del modelo llegue a la red;
  - `modelVersion` es `MODEL_VERSION` o el nombre del archivo del modelo, recortado a 60 caracteres;
  - `vin` y `odometerKm` van siempre en `null` (D9).
- **La URL del backend es la completa:** el adaptador hace `POST` a `MOTORIA_PHOTO_CLASSIFIER_URL` tal cual, así que se configura `http://<dirección en el túnel>:8080/v1/classify`.
- **Errores**, con cuerpo `{"code": "..."}` y sin repetir nada de la entrada:

| Estado | Causa | Lo que hace el adaptador |
|---|---|---|
| 400 | JSON mal formado, campos faltantes o de otro tipo, base64 inválido, imagen vacía o dañada | `PHOTO_CLASSIFIER_IMAGE_UNREADABLE` |
| 415 | `mimeType` distinto de JPEG, PNG o WebP, o bytes que no son del tipo declarado | `PHOTO_CLASSIFIER_IMAGE_UNREADABLE` |
| 422 | imagen con más píxeles que `MAX_IMAGE_PIXELS` | `PHOTO_CLASSIFIER_IMAGE_UNREADABLE` |
| 401 | `CLASSIFIER_TOKEN` configurado y el Bearer falta o no coincide | `PHOTO_CLASSIFIER_NOT_CONFIGURED` |
| 413 | cuerpo mayor que `MAX_BODY_BYTES` | `PHOTO_CLASSIFIER_UNAVAILABLE` |
| 500 | falla de la inferencia (`INFERENCE_FAILED`) o ninguna etiqueta conocida (`NO_KNOWN_LABEL`) | `PHOTO_CLASSIFIER_UNAVAILABLE` |
| 503 | no hay modelo cargado (`MODEL_NOT_LOADED`) | `PHOTO_CLASSIFIER_UNAVAILABLE` |

Para el vendedor todos son `UNAVAILABLE` y no bloquean. Un 413 no debería ocurrir: con `max-side` 1024 el cuerpo queda muy por debajo de 8 MiB. Si ocurre, es un desajuste de configuración y no una foto ilegible; por eso no se mapea a 4xx de imagen.

`expectedSlot` es solo contexto. Se exige que sea texto de hasta 40 caracteres, pero no se valida contra la lista: el modelo no lo usa, y así una toma nueva del backend no rompe el servicio. No se registra (D12 de `add-captura-guiada-fotos`).

## D2. Licencia: Ultralytics AGPL-3.0 en desarrollo, Enterprise antes del lanzamiento

Decisión del dueño, 2026-11-27: se usa Ultralytics YOLO con la licencia gratuita **AGPL-3.0** durante el desarrollo y se negocia la licencia **Enterprise** con Ultralytics antes del lanzamiento. Reemplaza la disyuntiva de la puerta 0.2 de `add-captura-guiada-fotos`, que queda registrada y abierta hasta firmar.

Obligaciones mientras rija la AGPL-3.0 (`LICENSE-NOTICE.md`):

- **Sección 13 (interacción por red):** quien interactúe con el servicio por una red tiene derecho al código fuente correspondiente de la versión que corre, con nuestras modificaciones. Mientras se use la AGPL, el código de `ml/photo-classifier/` debe estar disponible para quien interactúe con el servicio por red, y Motoria debe poder entregarlo a quien lo pida. El servicio solo lo llama el backend por la red privada, pero los vendedores cuyas fotos se clasifican podrían considerarse usuarios remotos.
- **Pesos entrenados y modelo exportado:** Ultralytics los considera obra derivada. Se tratan como AGPL hasta tener la Enterprise.
- **Aislamiento:** el backend Java y el portal solo hablan HTTP con el servicio. No importan, enlazan ni copian código de Ultralytics. El servicio es un proceso y un contenedor aparte, y Ultralytics se importa solo en `service/classifier.py` (`load_yolo`) y en `training/`.
- **Navegador:** no se distribuye el modelo al navegador (`onnxruntime-web`) mientras rija la AGPL.
- **Antes del lanzamiento (puerta 0.4):**
  - firmar la Enterprise, que cubra el servicio por red, los pesos y, si se quiere, el navegador;
  - actualizar `LICENSE-NOTICE.md` y el `license` de `pyproject.toml`.

  Sin la Enterprise firmada, el servicio no atiende fotos de usuarios reales.
- **Plan B:** un clasificador con licencia permisiva (`timm` o `torchvision`) detrás del mismo contrato. Solo cambian `service/classifier.py` y `training/`; el backend no cambia.

## D3. Arquitectura del servicio

- **FastAPI y uvicorn, un proceso** (`--workers 1`, `service/main.py`): el modelo se carga una sola vez en el `lifespan` de `create_app` con `load_yolo`.
- **Sin modelo, el servicio arranca igual:** si falta `MODEL_PATH`, el archivo no existe, la carga falla o el modelo no tiene ninguna de las 15 etiquetas, se registra solo el tipo de error. `/health` responde 503 y `/v1/classify` responde 503. Así el contenedor no entra en un ciclo de reinicios y el estado se ve en el chequeo de salud.
- **Inferencia fuera del bucle de eventos:** la decodificación y la predicción corren en el pool de hilos (`run_in_threadpool`). El predictor de Ultralytics no es seguro entre hilos, así que `YoloClassifier` serializa las predicciones con un candado. Para más capacidad se agregan CPU o contenedores.
- **Formato del modelo:** `.pt` o `.onnx`. Ultralytics carga el ONNX con ONNX Runtime y lee las etiquetas de sus metadatos. En producción se sirve el ONNX (D7).
- **Inyección para pruebas:** `create_app(settings, classifier=..., loader=..., readings=...)`. Las pruebas pasan un clasificador falso y nunca importan Ultralytics.
- **Orden en `/v1/classify`:**
  1. token;
  2. modelo cargado;
  3. lectura del cuerpo con límite;
  4. forma del JSON;
  5. base64;
  6. imagen;
  7. predicción;
  8. filtrado;
  9. lecturas (D9).

  El token va primero para no gastar trabajo en llamadas no autorizadas.

## D4. Seguridad

- **Token opcional** (`CLASSIFIER_TOKEN`): `Authorization: Bearer <token>`, comparado con `hmac.compare_digest`. Vacío significa sin autenticación, porque el servicio vive en la red privada. Es el mismo valor que `MOTORIA_PHOTO_CLASSIFIER_TOKEN`.
- **Límite del cuerpo** (`MAX_BODY_BYTES`, 8 MiB): se rechaza por `Content-Length` y, sin él, contando mientras se lee el flujo.
- **Decodificación segura** (`service/imaging.py`):
  - base64 estricto (`validate=True`), con una cota de longitud antes de decodificar;
  - solo el formato que corresponde al `mimeType` (`Image.open(..., formats=[...])`): JPEG, PNG o WebP;
  - se lee el encabezado y se rechaza (422) si ancho por alto supera `MAX_IMAGE_PIXELS` (16 777 216), antes de decodificar los píxeles; con ese límite salta antes que el aviso de Pillow, y si Pillow lanza `DecompressionBombError` también responde 422 (no se usa `warnings.catch_warnings`, que no es seguro entre hilos);
  - imagen truncada o dañada: 400. No se activa `LOAD_TRUNCATED_IMAGES`;
  - se convierte a RGB en memoria y se cierra al terminar.
- **Sin superficie extra:**
  - sin `/docs`, `/redoc` ni `/openapi.json`;
  - `--no-server-header` y `--no-access-log`;
  - sin `subprocess`, `eval`, `exec` ni `pickle` de entradas externas. El modelo es un archivo propio montado de solo lectura, nunca una entrada de la red.
- **Contenedor:**
  - usuario no root (uid 10001) y sistema de archivos de solo lectura con `/tmp` en tmpfs;
  - `cap_drop: ALL` y `no-new-privileges`;
  - límites de CPU y memoria;
  - el modelo se monta en `/models` de solo lectura.
- **Red:** el puerto se publica solo en la dirección de la VM dentro del túnel WireGuard (10.90.0.0/24), nunca en la interfaz pública. El firewall de la VM acepta el puerto solo desde la dirección de producción en el túnel. Ni el repositorio ni este diseño llevan direcciones públicas, llaves ni tokens.

## D5. Privacidad

- La imagen y el base64 viven solo en memoria durante la solicitud. No se escriben a disco ni se registran.
- Tampoco se registran `expectedSlot` ni el `mimeType`. Los logs llevan la latencia y el tipo de error.
- **Sin telemetría de Ultralytics:**
  - `YOLO_OFFLINE=true` en la imagen;
  - `settings.update({"sync": False})` en `load_yolo` y en cada script de `training/` (`training/common.py`);
  - en la VM, el contenedor no necesita salida a internet.
- El dataset (D6) se recodifica sin EXIF ni GPS. `.gitignore` impide versionar fotos, datasets, pesos, modelos y reportes.

## D6. Dataset y entrenamiento

- **Origen** (README, «Dataset»):
  - fotos de anuncios de vendedores con el consentimiento `MODEL_TRAINING` vigente, que ya existe en `ConsentType` (Ley 8968; que su texto cubra este uso está **por verificar**, puerta 0.1);
  - fotos corregidas por el analista en la bandeja de `add-captura-guiada-fotos`;
  - fotos propias o de aliados con licencia;
  - nunca fotos de terceros sin licencia;
  - sin placas ni rostros legibles en lo que se conserve.
- **Mínimo por clase:**
  - 300 de entrenamiento para el primer candidato (`--min-per-class`);
  - 1 000 o más en esquinas y laterales para producción;
  - conjunto dorado de al menos 100 por clase.
- **Etiquetado:**
  - la toma declarada más la revisión humana; el conjunto dorado lo revisan dos personas;
  - izquierda y derecha son las del vehículo, como en `slotGuides` del portal;
  - una foto ambigua queda fuera del dorado.
- **`training/prepare_dataset.py`:**
  - entrada: CSV (`path,slot,vehicle_id,split,source`) o una carpeta por etiqueta;
  - las rutas fuera de `--images-root` se descartan (`resolve_inside`);
  - **división estratificada por toma y agrupada por vehículo** con semilla fija (`assign_splits`). Un vehículo nunca queda en dos divisiones y la columna `split` fija el conjunto dorado;
  - normaliza cada imagen como el backend: la rota según el EXIF, reduce el lado mayor a 1024 y la guarda como JPEG sin metadatos. Así el modelo entrena con lo mismo que recibirá;
  - deja `manifest.csv` y `summary.json`, avisa si una clase queda bajo el mínimo y falla si a una división le falta una clase (Ultralytics alinea las clases por carpeta);
  - sin `subprocess`.
- **`training/train.py`:**
  - `YOLO(model, task="classify").train(...)` con hiperparámetros por argumento (por defecto `yolo11n-cls.pt`, 224 px, AdamW, `lr0` 0.001, 60 épocas, paciencia 15);
  - semilla fija y `deterministic`;
  - **`fliplr=0`**: voltear cambia la toma (LEFT por RIGHT, las esquinas entre sí);
  - comprueba que las carpetas de `train` sean etiquetas conocidas y estén en `val`;
  - guarda `train_summary.json` con argumentos, métricas, etiquetas y versión de Ultralytics.

## D7. Métricas y exportación

- **`training/evaluate.py`** usa el mismo filtrado del servicio (`rank`) y la copia exacta de `PhotoAnglePolicy.decide` (`decide`). Calcula:
  - exactitud top-1 global, en exteriores, en detalle y en `OTHER`;
  - matriz de confusión (`confusion_matrix.csv`) y precisión y exhaustividad por clase;
  - **vendedor honesto** (la foto es la toma pedida): cuántas quedarían `MATCH`, `MISMATCH` (falso rechazo) y `UNCERTAIN` con 0.80 y 0.90;
  - **foto equivocada** (cada foto enviada para cada una de las 14 tomas distintas de la suya: 13 si la foto es de una toma, 14 si es `OTHER`): rechazos correctos, aceptaciones por error y dudosas;
  - barrido de umbrales (`thresholdSweep`) para ajustar `accept-threshold` y `reject-threshold`;
  - latencia p50 y p95 por imagen, con calentamiento, en la máquina de la evaluación;
  - `meetsMinimums`.
- **Métricas mínimas** antes de conectar el proveedor `http` (propuestas en D11 de `add-captura-guiada-fotos`; las confirma el dueño en la puerta 0.2; aquí el 90 % se aplica a todas las tomas de detalle, no solo a interiores):
  - top-1 ≥ 95 % en exteriores y ≥ 90 % en detalle;
  - falsos `MISMATCH` < 1 % con los umbrales del backend;
  - latencia p95 < 300 ms en la VM de IA.
- **`training/export.py`:**
  - ONNX (opset 17, entrada fija);
  - deja `<versión>.onnx` y `<versión>.json` con las etiquetas en orden, el tamaño de entrada, los sha256 del ONNX y de los pesos, y la versión de Ultralytics;
  - se vuelve a evaluar el ONNX en CPU antes de desplegarlo.
- **Futuro, no implementado:** el mismo ONNX en el navegador con `onnxruntime-web`, como otra implementación de `OnDeviceDetector` (D8 de `add-captura-guiada-fotos`). Requiere la licencia Enterprise (D2) y medir tamaño y latencia en gama media.

## D8. Despliegue

- **`Dockerfile`:**
  - `python:3.11.10-slim-bookworm` con las bibliotecas de sistema de OpenCV;
  - `torch` y `torchvision` en su versión CPU: en x86_64 con el sufijo `+cpu` del índice de PyTorch y en aarch64 (la VM de IA es Ampere, comprobado el 2026-11-27 con `uname -m`) desde PyPI, que ya es solo CPU; lo eligen marcadores `platform_machine` en `requirements.txt`;
  - usuario no root, sin modelo dentro y con `HEALTHCHECK` contra `/health`;
  - sin barras invertidas de continuación.
- **`.dockerignore`:** solo entran `service/`, los requirements, el README y el aviso de licencia.
- **`deploy/docker-compose.yml` y `deploy/photo-classifier.env.example`**, para la VM de IA:
  - publica en `${WG_BIND_ADDRESS}:8080`, que es obligatoria;
  - monta `MODELS_DIR` de solo lectura;
  - `MODEL_FILE` y `MODEL_VERSION` son obligatorias, el token es opcional;
  - el `.env` real lo ignora git.
- **Backend** (nombres reales de `application.properties`, sin cambios):

| Variable | Propiedad | Valor |
|---|---|---|
| `MOTORIA_PHOTO_CLASSIFIER_PROVIDER` | `motoria.media.angle-check.provider` | `http` |
| `MOTORIA_PHOTO_CLASSIFIER_URL` | `motoria.media.angle-check.url` | `http://<WG_BIND_ADDRESS>:8080/v1/classify` |
| `MOTORIA_PHOTO_CLASSIFIER_TOKEN` | `motoria.media.angle-check.token` | igual a `CLASSIFIER_TOKEN`, opcional |
| `MOTORIA_PHOTO_ANGLE_CHECK_ENABLED` | `motoria.media.angle-check.enabled` | `true` |

  `timeout` (PT5S), los umbrales y `max-side` se ajustan en `application.properties`.

## D9. Punto de extensión del OCR

YOLO clasifica la imagen completa y no lee texto. `service/readings.py` define `ReadingExtractor.extract(image, predicted_slot) -> Readings(vin, odometer_km)`, y por defecto se usa `NoReadings`, que siempre responde vacío.

El OCR de la puerta 0.5 (detector de la región del VIN y del odómetro más un OCR) se implementa como otro `ReadingExtractor` y se pasa a `create_app(readings=...)`. Una falla del extractor no pierde la clasificación: se registra el tipo de error y `vin` y `odometerKm` van en `null`. El backend ya normaliza y compara los valores (D6 de `add-captura-guiada-fotos`).

## D10. Dependencias

- Versiones fijas, sin instalar ni probar juntas en este cambio (puerta 0.3):
  - `requirements-api.txt`: FastAPI 0.115.6, uvicorn 0.32.1, Pillow 11.1.0;
  - `requirements.txt`: imagen CPU con torch 2.6.0, torchvision 0.21.0, ultralytics 8.3.100, onnx 1.17.0 (Ultralytics lo exige al cargar un ONNX) y onnxruntime 1.20.1;
  - `requirements-train.txt`: torch 2.6.0 y torchvision 0.21.0 fijados, ultralytics 8.3.100, onnx 1.17.0 y onnxruntime 1.20.1;
  - `requirements-dev.txt`: pytest 8.3.4 y httpx 0.27.2.
- torch 2.6.0 en adelante, porque las anteriores tienen la vulnerabilidad crítica de `torch.load` (CVE-2025-32434).
- Antes de desplegar, actualizar a las versiones vigentes y pasar el escaneo de vulnerabilidades. Ultralytics instala sus requisitos faltantes en tiempo de ejecución: por eso la exportación usa `simplify=False` por defecto (sin `onnxslim`) y la imagen corre con `YOLO_OFFLINE=true` y `YOLO_AUTOINSTALL=false`.

## D11. Pruebas

pytest con `TestClient` de FastAPI y un clasificador falso (`tests/helpers.py`), sin Ultralytics ni torch:

- `tests/test_app.py`: el contrato y los errores del servicio;
- `tests/test_config_and_labels.py`: configuración y etiquetas;
- `tests/test_evaluate.py`: la regla de umbrales y las métricas;
- `tests/test_prepare_dataset.py`: la división y la normalización.

Las pruebas no se ejecutaron. No hay un job de CI de Python en el repositorio: agregarlo es parte de la puerta 0.3.
