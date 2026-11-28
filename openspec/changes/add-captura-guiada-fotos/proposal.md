# Captura guiada de fotos con RA y validación del ángulo con un modelo local

## Why

En F1 el vendedor sube las 14 fotos del protocolo como archivos sueltos. Nada comprueba que la foto del «Frente» sea el frente, ni que la del tablero muestre el odómetro. El analista lo descubre tarde, al revisar el anuncio, y el vendedor tiene que volver a empezar.

El dueño decidió (2026-11-27):

- el cliente sube todas las fotos de una vez y la web debe guiar cada toma y validar que tenga el ángulo correcto, con realidad aumentada donde se pueda;
- la validación del ángulo **no** usa un proveedor de visión externo ni el `LlmPort`: Motoria entrenará su propio modelo local (probablemente YOLO de Ultralytics) y lo servirá en su infraestructura. No hay costo por llamada.

Implementa la capacidad «captura guiada de fotos» de F1 en el roadmap (design de `define-plataforma-motoria`, fila F1, y requirement «Protocolo de fotos» de `publicacion-anuncios`). También deja preparada la lectura de VIN y odómetro de las fotos que el roadmap pide para el antifraude de kilometraje («Verificación del kilometraje» de `inspeccion-ia`).

## What Changes

- **Captura guiada en el portal** (paso «Fotos» del asistente de venta):
  - cámara trasera (`getUserMedia`, `facingMode: environment`) con la silueta de la toma superpuesta e instrucción traducida;
  - en las 8 tomas exteriores, detección del vehículo en el dispositivo con MediaPipe Tasks Vision (cargado perezosamente desde un CDN con versión fija, sin dependencia npm) y captura automática cuando el vehículo queda dentro de la silueta de forma estable; siempre hay botón manual;
  - la detección está detrás de una capa abstracta (`OnDeviceDetector`) para reemplazarla por el mismo modelo local exportado al navegador cuando exista;
  - interior, tablero, motor, llantas y VIN: solo silueta y guía;
  - sin cámara, sin permiso o sin MediaPipe: silueta con captura manual; subir un archivo sigue disponible como hoy.
- **Realidad aumentada en Android** con WebXR (`immersive-ar` y `hit-test`), detrás de la bandera `VITE_AR_ENABLED` y de la detección de capacidad: marcas en el suelo con los puntos de toma alrededor del auto. Ante cualquier falla, vuelve a la cámara guiada. **La RA nativa (ARKit, ARCore) queda fuera** hasta que exista app nativa.
- **Validación del ángulo en el servidor con un clasificador local:**
  - puerto nuevo `PhotoAngleClassifierPort` en `media/integration` con adaptadores `stub` (por defecto, nunca sale a la red) y `http` (servicio de inferencia propio, `MOTORIA_PHOTO_CLASSIFIER_URL`);
  - al adjuntar la foto de una toma, en la misma solicitud y sin transacción abierta durante la llamada: `MATCH`, `MISMATCH` (con la toma que parece ser), `UNCERTAIN` o `UNAVAILABLE`, decidido por umbrales configurables;
  - `MISMATCH` obliga a repetir la foto con un motivo traducido; `UNCERTAIN` y `UNAVAILABLE` no bloquean y van al analista;
  - se guarda la versión del modelo y la latencia, nunca la imagen ni la respuesta cruda.
- **VIN y odómetro leídos de las fotos** (solo si el servicio de inferencia los devuelve; YOLO no lee texto, el OCR es una puerta del dueño):
  - el VIN se compara con el de la ficha registral y el odómetro con el kilometraje declarado (tolerancia configurable);
  - una discrepancia es una anomalía nueva (`VIN_MISMATCH`, `ODOMETER_MISMATCH`) que se muestra traducida, se publica con el evento existente `vehicle.registry.anomaly.detected` para que `notification` avise al vendedor cuando exista el canal (puerta 0.6 de tasks) y el analista la ve en el backoffice.
- **Backoffice:** bandeja «Ángulo de las fotos» (`GET /backoffice/photo-reviews`) y decisión del analista (`POST /backoffice/photo-reviews/{listingId}/{slot}`, BACKOFFICE y ADMIN, auditado). El analista no revisa un anuncio suyo. Rechazar una foto de un anuncio en revisión lo devuelve a borrador.
- **Publicación:** `ListingPublicationGuard` no deja enviar ni publicar con fotos por repetir (`LISTING_PHOTOS_TO_REPEAT`).
- **Migración V10** (`V10__listing_photo_angle_check.sql`), en el rango de F1.
- **Fuera de este cambio:** el entrenamiento del modelo y su servicio de inferencia (puerta 0.1, design D11), el OCR (puerta 0.3) y la RA nativa.

## Impact

- Specs: delta de `publicacion-anuncios` (captura guiada, validación del ángulo, lecturas, revisión del analista, regla de publicación).
- Código backend:
  - `media`: puerto, adaptadores y productor del clasificador, `PhotoAngleCheckService`, `PhotoAngleImageReader`, `PhotoAnglePolicy`, códigos `PHOTO_CLASSIFIER_*`;
  - `common/port`: `PhotoAngleCheckResult` y `MediaPort.checkPhotoAngle`;
  - `listing`: dominio de la foto (estado, lecturas, decisión), `ListingPhotoService` sin transacción alrededor del clasificador, `ListingPhotoAngleApplier`, `ListingPhotoReadingChecker`, `ListingPhotoReviewService`, `BackofficePhotoReviewResource`, `ListingPublicationPolicy`, `ListingPublicationGuard`, mapper y DTO;
  - `notification`: solo documentación (el consumidor ya acepta el payload).
- Portal: `PhotosStep` y la carpeta nueva `pages/sell/capture/`. Backoffice: `PhotoReviewsPage`. Además `@motoria/api`, `@motoria/ui` (estilos) y `@motoria/i18n` (es y en).
- Variables nuevas: backend `MOTORIA_PHOTO_CLASSIFIER_PROVIDER`, `MOTORIA_PHOTO_CLASSIFIER_URL`, `MOTORIA_PHOTO_CLASSIFIER_TOKEN` (opcional), `MOTORIA_PHOTO_ANGLE_CHECK_ENABLED`; portal `VITE_MEDIAPIPE_BASE_URL`, `VITE_MEDIAPIPE_MODEL_URL`, `VITE_AR_ENABLED`.
- Despliegue: la CSP y la Permissions-Policy del portal deben permitir cámara, `xr-spatial-tracking`, el CDN de MediaPipe y el modelo (design D10). En el repositorio no hay configuración del servidor web del portal: queda como puerta 0.4.
- Sin cambios en RabbitMQ: el evento de anomalía y su cola ya existen.
- Supuestos por verificar: rendimiento de MediaPipe en gama media, soporte de WebXR `immersive-ar` en los Android de los vendedores, latencia del servicio de inferencia en la VM de IA y la licencia de Ultralytics (AGPL-3.0, puerta 0.2).
