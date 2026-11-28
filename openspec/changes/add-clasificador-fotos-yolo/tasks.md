# Tasks

Marcas: (A) agente, (B) puerta del dueño, (C) calendario no acelerable. Una marca [x] significa «escrito», nunca «ejecutado, instalado, entrenado ni probado». Nada de este cambio se instaló, ejecutó ni entrenó: lo confirma el CI o el dueño. Sin migraciones.

Rutas de evidencia relativas a `ml/photo-classifier/` salvo que se indique otra.

## 0. Puertas

- [ ] 0.1 (B) Dataset y base legal (design D6):
  - confirmar con el asesor legal que el consentimiento `MODEL_TRAINING` («Mejora de modelos de IA», Ley 8968) cubre el uso de las fotos de anuncios para entrenar;
  - exportar las fotos de vendedores con ese consentimiento vigente, más las propias o de aliados con licencia, sin placas ni rostros legibles;
  - etiquetar con la guía del README, con un conjunto dorado revisado dos veces y separado por vehículo;
  - al menos 300 fotos de entrenamiento por clase para el primer candidato.
- [ ] 0.2 (B) Entrenar y alcanzar las métricas mínimas (design D7):
  - confirmar o ajustar los mínimos propuestos antes de evaluar (y, si cambian, `training/evaluate.py`);
  - `prepare_dataset`, `train`, `evaluate` y `export`;
  - propuesta: top-1 ≥ 95 % en exteriores y ≥ 90 % en detalle, falsos `MISMATCH` < 1 % con 0.80 y 0.90, y latencia p95 < 300 ms en la VM de IA;
  - ajustar `accept-threshold` y `reject-threshold` del backend con el barrido de `evaluate` si hace falta.
- [ ] 0.3 (B) Desplegar en la VM de IA (design D8):
  - actualizar las versiones fijas y pasar el escaneo de vulnerabilidades (design D10);
  - agregar un job de CI de Python (pytest) para `ml/photo-classifier/`;
  - construir la imagen y levantar el compose publicado solo en la dirección del túnel WireGuard, con el firewall que acepte el puerto solo desde producción;
  - configurar en el backend `MOTORIA_PHOTO_CLASSIFIER_PROVIDER=http`, `MOTORIA_PHOTO_CLASSIFIER_URL=http://<WG_BIND_ADDRESS>:8080/v1/classify` y, si se usa, `MOTORIA_PHOTO_CLASSIFIER_TOKEN`;
  - comprobar `/health` desde producción y una foto de punta a punta.
- [ ] 0.4 (B) Licencia Enterprise de Ultralytics firmada **antes del lanzamiento** (design D2). Mientras tanto rige la AGPL-3.0: el código de `ml/photo-classifier/` debe estar disponible para quien interactúe con el servicio por red. Al firmar, actualizar `LICENSE-NOTICE.md`, `pyproject.toml` y la puerta 0.2 de `add-captura-guiada-fotos`. Si no prospera, plan B con un clasificador permisivo detrás del mismo contrato.
- [ ] 0.5 (B) OCR del VIN y del odómetro (detector de la región más OCR) como `ReadingExtractor` (design D9). Es la puerta 0.3 de `add-captura-guiada-fotos`.

## 1. Servicio de inferencia

- [x] 1.1 (A) Contrato `POST /v1/classify` y `GET /health`, códigos de error del adaptador Java, inyección del clasificador y carga única al arranque (design D1 y D3). Evidencia: `service/app.py` (`create_app`, `classify`, `health`, `_parse_request`, `_classify_sync`) y `service/main.py`.
- [x] 1.2 (A) Etiquetas iguales a `KNOWN_SLOTS` del backend y filtrado de las desconocidas, top-k y versión de hasta 60 caracteres. Evidencia: `service/labels.py` y `service/classifier.py` (`rank`, `model_version_for`).
- [x] 1.3 (A) Clasificador Ultralytics de imagen completa (`.pt` u `.onnx`), con candado por proceso, sin telemetría y con 503 si no hay modelo usable. Evidencia: `service/classifier.py` (`YoloClassifier`, `load_yolo`, `ModelNotUsable`).
- [x] 1.4 (A) Seguridad: token en tiempo constante, límite del cuerpo, decodificación segura con límite de píxeles y formatos, y sin docs (design D4). Evidencia: `service/app.py` (`_check_token`, `_read_body`) y `service/imaging.py` (`decode_base64`, `decode_image`).
- [x] 1.5 (A) Configuración por entorno. Evidencia: `service/config.py` (`Settings.from_env`).
- [x] 1.6 (A) Punto de extensión del OCR (design D9). Evidencia: `service/readings.py` (`ReadingExtractor`, `NoReadings`) y `service/app.py` (`readings`).

## 2. Entrenamiento (scripts sin ejecutar)

- [x] 2.1 (A) Dataset con división estratificada por toma y agrupada por vehículo, semilla fija, normalización como el backend y guardia de rutas (design D6). Evidencia: `training/prepare_dataset.py` (`assign_splits`, `resolve_inside`, `normalize_image`, `summarize`).
- [x] 2.2 (A) Entrenamiento con Ultralytics classify, sin volteo y con métricas guardadas. Evidencia: `training/train.py` (`train_arguments`, `check_dataset`).
- [x] 2.3 (A) Evaluación con la regla de umbrales del backend, matriz de confusión, vendedor honesto, foto equivocada, barrido y latencia (design D7). Evidencia: `training/evaluate.py` (`decide`, `honest_seller`, `wrong_shot`, `threshold_sweep`, `build_report`).
- [x] 2.4 (A) Exportación a ONNX con metadatos y sha256. Evidencia: `training/export.py` y `training/common.py`.

## 3. Despliegue y documentación

- [x] 3.1 (A) Imagen slim, usuario no root, sin modelo, con chequeo de salud (design D8). Evidencia: `Dockerfile` y `.dockerignore`.
- [x] 3.2 (A) Compose para la VM de IA, publicado solo en la dirección del túnel, con ejemplo de variables sin secretos. Evidencia: `deploy/docker-compose.yml` y `deploy/photo-classifier.env.example`.
- [x] 3.3 (A) Dependencias con versiones fijas (design D10). Evidencia: `requirements-api.txt`, `requirements.txt`, `requirements-train.txt`, `requirements-dev.txt` y `pyproject.toml`.
- [x] 3.4 (A) Aviso de licencia AGPL-3.0 y README: origen de las fotos, base legal, mínimo por clase, guía de etiquetado, métricas y configuración del backend (design D2 y D6). Evidencia: `LICENSE-NOTICE.md` y `README.md`.
- [x] 3.5 (A) Nada de fotos, pesos, modelos ni `.env` en git. Evidencia: `.gitignore`.
- [x] 3.6 (A) Registro de la decisión de licencia en la puerta 0.2 del cambio anterior, que sigue abierta. Evidencia: `openspec/changes/add-captura-guiada-fotos/tasks.md` (0.2).

## 4. Pruebas (escritas, no ejecutadas)

- [x] 4.1 (A) Contrato y errores del servicio:
  - contrato exacto, orden y top-k, y etiquetas desconocidas o confianzas inválidas filtradas;
  - modelo sin etiquetas conocidas (500) y versión recortada;
  - PNG con transparencia y WebP a RGB;
  - tipo no admitido, bytes que no son imagen y PNG declarado como JPEG (415); JPEG truncado, base64 inválido e imagen vacía (400);
  - límite de píxeles (422) y solicitudes mal formadas (400);
  - cuerpo excesivo con y sin `Content-Length` (413);
  - token (401) y orden de las comprobaciones;
  - modelo ausente (503) y `/health`, cargador al arranque y cargador que falla;
  - falla de la inferencia (500) y extensión del OCR, también cuando falla;
  - docs ocultas.

  Evidencia: `tests/test_app.py`.
- [x] 4.2 (A) Configuración, etiquetas y ordenamiento. Evidencia: `tests/test_config_and_labels.py`.
- [x] 4.3 (A) Regla de umbrales idéntica a `PhotoAnglePolicy`, matriz, métricas y mínimos. Evidencia: `tests/test_evaluate.py`.
- [x] 4.4 (A) División determinista, vehículo en una sola división, split fijado, guardia de rutas, normalización sin EXIF y estructura de Ultralytics. Evidencia: `tests/test_prepare_dataset.py`.
- [x] 4.5 (A) Clasificador falso inyectable, sin Ultralytics. Evidencia: `tests/helpers.py` y `tests/conftest.py`.
- [ ] 4.6 (A) Prueba de integración con un modelo real (`load_yolo` con un `.onnx` pequeño): pendiente, depende de la puerta 0.2.

## 5. Fuera de este cambio

- Entrenar y desplegar el modelo (puertas 0.1 a 0.3).
- OCR del VIN y del odómetro (puerta 0.5).
- Modelo en el navegador con `onnxruntime-web` (design D7). Requiere la licencia Enterprise.
- Validación asíncrona por evento (D2 de `add-captura-guiada-fotos`), si la latencia medida lo pide.
