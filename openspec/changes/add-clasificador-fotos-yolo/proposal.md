# Servicio de inferencia y entrenamiento YOLO para el ángulo de las fotos

## Why

`add-captura-guiada-fotos` dejó listo el backend para validar el ángulo de cada foto con un modelo local propio: el puerto `PhotoAngleClassifierPort`, el adaptador `HttpPhotoAngleClassifierAdapter` y el contrato HTTP de su design D3. Falta el otro extremo: el servicio que responde ese contrato y el modelo que lo alimenta (puertas 0.1 y 0.2 de ese cambio). Hasta entonces el backend usa el `stub`, que siempre responde la toma esperada.

El dueño decidió (2026-11-27):

- usar **Ultralytics YOLO con la licencia gratuita AGPL-3.0** durante el desarrollo;
- negociar la **licencia Enterprise** con Ultralytics **antes del lanzamiento**.

Implementa la parte de IA de la capacidad «captura guiada de fotos» de F1 del roadmap (design de `define-plataforma-motoria`, fila F1, y requirement «Protocolo de fotos» de `publicacion-anuncios`). También es la base de «Integridad de las fotos» de `inspeccion-ia`.

## What Changes

- **Carpeta nueva `ml/photo-classifier/`**, fuera del backend Java:
  - **servicio de inferencia** en Python 3.11 con FastAPI y uvicorn:
    - `POST /v1/classify` con el contrato exacto de D3 de `add-captura-guiada-fotos` y los códigos de error que espera el adaptador Java;
    - `GET /health` con el modelo cargado y su versión;
    - modelo de clasificación de Ultralytics (por ejemplo `yolo11n-cls`) cargado una vez al arranque desde un volumen;
    - token Bearer opcional en tiempo constante, límite del cuerpo y de los píxeles, solo JPEG, PNG y WebP;
    - sin guardar ni registrar la imagen;
  - **entrenamiento**, en scripts que no se ejecutaron:
    - armado del dataset con división estratificada por toma y agrupada por vehículo, con semilla fija;
    - entrenamiento sin volteo horizontal;
    - evaluación con la misma regla de umbrales del backend (MATCH, MISMATCH, UNCERTAIN) y las métricas mínimas;
    - exportación a ONNX;
  - **despliegue:** `Dockerfile` sin modelo dentro y con usuario no root, y docker compose para la VM de IA, publicado solo en la dirección del túnel WireGuard;
  - **pruebas** con pytest y un clasificador falso, escritas y sin ejecutar;
  - **aviso de licencia AGPL-3.0** (`LICENSE-NOTICE.md`) y README con el origen de las fotos, la base legal, el mínimo por clase, la guía de etiquetado, las métricas y la configuración del backend.
- **`add-captura-guiada-fotos/tasks.md`:** la puerta 0.2 registra la decisión de licencia. Sigue abierta hasta tener la licencia Enterprise.
- **Sin cambios** en el backend, el portal ni las migraciones. El contrato ya existe, y las propiedades `motoria.media.angle-check.*` y sus variables `MOTORIA_PHOTO_CLASSIFIER_*` se usan tal cual.
- **Fuera de este cambio:**
  - entrenar el modelo de verdad (puertas 0.1 y 0.2);
  - desplegarlo (puerta 0.3);
  - el OCR del VIN y del odómetro: queda un punto de extensión (puerta 0.5);
  - el modelo en el navegador.

## Impact

- Specs: delta de `publicacion-anuncios` (servicio de inferencia del ángulo, entrada segura, privacidad, dataset con base legal, métricas mínimas, licencia).
- Código nuevo, todo en `ml/photo-classifier/`:
  - `service/`, `training/` y `tests/`;
  - `requirements*.txt`, `pyproject.toml`, `Dockerfile`, `.dockerignore`, `.gitignore`;
  - `deploy/`, `README.md` y `LICENSE-NOTICE.md`.
- Variables nuevas del servicio: `MODEL_PATH`, `MODEL_VERSION`, `CLASSIFIER_TOKEN`, `MAX_BODY_BYTES`, `MAX_IMAGE_PIXELS`, `TOP_K`, `IMAGE_SIZE`, `DEVICE`, `LOG_LEVEL`. En el backend no hay variables nuevas: `MOTORIA_PHOTO_CLASSIFIER_PROVIDER=http`, `MOTORIA_PHOTO_CLASSIFIER_URL` y `MOTORIA_PHOTO_CLASSIFIER_TOKEN` ya existen.
- Infraestructura: un contenedor en la VM de IA, escuchando solo en la dirección del túnel WireGuard (10.90.0.0/24).
- Licencia: mientras se use la AGPL-3.0, el código de `ml/photo-classifier/` debe estar disponible para quien interactúe con el servicio por red. Antes del lanzamiento hace falta la licencia Enterprise (puerta 0.4).
- Supuestos **por verificar**:
  - que el consentimiento `MODEL_TRAINING` («Mejora de modelos de IA») cubra este uso según la Ley 8968;
  - las condiciones y el costo de la licencia Enterprise de Ultralytics;
  - la latencia real en la CPU de la VM de IA;
  - que las versiones fijas de las dependencias instalen juntas y pasen el escaneo de vulnerabilidades.
