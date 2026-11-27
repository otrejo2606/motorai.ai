# Design

## D1. Cambio de rumbo: modelo local, sin proveedor de visión

El primer encargo pedía validar el ángulo con un modelo de visión vía `LlmPort` (proveedor externo, costo por foto). El dueño lo cambió el 2026-11-27: Motoria entrenará y servirá su propio modelo (probablemente YOLO de Ultralytics).

Consecuencias en el código:

- no se toca `LlmPort`, `LlmRequest`, `HttpLlmAdapter`, `AiPrompts` ni `AiUseCase`;
- no se usa `AiInferenceRecorder`: no hay tokens ni costo por llamada. La trazabilidad es la versión del modelo y la latencia, guardadas con el resultado de cada foto (D5);
- el puerto vive en `media/integration` (`PhotoAngleClassifierPort`), porque es `media` quien tiene la imagen y sus límites de decodificación.

## D2. Flujo: síncrono en el adjunto, sin transacción alrededor de la llamada

Se eligió validar **en la misma solicitud** que adjunta la foto (`PUT /listings/{id}/photos/{slot}`), y no con un evento asíncrono:

- un clasificador pequeño (por ejemplo YOLO11n-cls a 224 px) debería responder en decenas o pocos cientos de milisegundos en CPU (supuesto sin medir, puerta 0.1); no hace falta cola;
- el vendedor ve el resultado en la respuesta, sin sondeo ni estado intermedio en el portal;
- no hay topología nueva de RabbitMQ (cola, enlace, DLQ) que preaprovisionar;
- el tiempo máximo está acotado por `motoria.media.angle-check.timeout` (PT5S). Un tiempo agotado es `UNAVAILABLE`, que no bloquea.

Orquestación, como el prepare y apply de `VehicleRegistryLookupService`:

1. `ListingPhotoAngleApplier.attach` (transacción): las validaciones de F1 (dueño, `DRAFT`, toma del protocolo, foto confirmada del vendedor) y la foto queda `PENDING`.
2. `MediaPort.checkPhotoAngle` **sin transacción**: `PhotoAngleImageReader.read` lee el original en su propia transacción corta y deja la lectura en la bitácora de acceso (`PHOTO_ANGLE_CHECK`); luego se reduce la imagen y se llama al clasificador sin ninguna transacción abierta.
3. `ListingPhotoAngleApplier.apply` (transacción): guarda el resultado solo si la toma sigue teniendo esa misma foto en `PENDING`, compara las lecturas (D6) y devuelve el anuncio.

`ListingPhotoService.attach` no es transaccional a propósito. Una excepción inesperada del paso 2 se guarda como `UNAVAILABLE`.

Se reconsiderará el flujo asíncrono (evento por el outbox y consumidor) si el modelo crece o la latencia medida en la puerta 0.1 pasa de un segundo.

## D3. Puerto y contrato del servicio de inferencia

`PhotoAngleClassifierPort.classify(expectedSlot, image, mimeType)` devuelve `PhotoAngleClassification` (toma predicha, confianza de 0 a 1, versión del modelo, `vin` y `odometerKm` opcionales, latencia) o lanza `PhotoAngleClassifierUnavailableException` con un código estable.

Adaptadores, elegidos por `motoria.media.angle-check.provider` (`MOTORIA_PHOTO_CLASSIFIER_PROVIDER`):

- `stub` (por defecto; cualquier valor que no sea `http` cae aquí): nunca sale a la red ni mira la imagen. Responde la toma esperada con `stub.confidence` (0.99). Para probar los otros caminos en desarrollo: `stub.predicted-slot` fuerza la toma predicha (MISMATCH), una confianza baja da UNCERTAIN y `stub.vin` y `stub.odometer-km` simulan una lectura.
- `http`: `java.net.http.HttpClient` contra el servicio propio (`MOTORIA_PHOTO_CLASSIFIER_URL`), con tiempo máximo configurable. El token es opcional (`MOTORIA_PHOTO_CLASSIFIER_TOKEN`, como `Bearer`), porque el servicio vive en la red privada.

Contrato HTTP (a implementar por el servicio de la puerta 0.1):

- Solicitud: `POST {url}`, `Content-Type: application/json`:

  ```json
  {"expectedSlot": "FRONT", "mimeType": "image/jpeg", "imageBase64": "..."}
  ```

  La imagen es un JPEG sin metadatos, con el lado mayor reducido a `motoria.media.angle-check.max-side` (1024 px). `expectedSlot` es contexto: el modelo predice por su cuenta.
- Respuesta 200:

  ```json
  {"modelVersion": "angle-yolo11n-cls-2027.01",
   "predictions": [{"slot": "FRONT", "confidence": 0.93}, {"slot": "FRONT_LEFT", "confidence": 0.05}],
   "vin": null, "odometerKm": null}
  ```

  - `slot` es una de las 14 tomas del protocolo u `OTHER`; se toma la de mayor confianza. Se descartan las etiquetas que no son una de esas 15 y las que tienen confianza fuera de 0 a 1.
  - `modelVersion` es obligatoria (hasta 60 caracteres).
  - `vin` y `odometerKm` son opcionales y solo los manda un servicio con OCR (puerta 0.3). Un VIN se normaliza a letras y dígitos (5 a 30); un odómetro fuera de 0 a 2 000 000 km se descarta.
- Errores, todos `UNAVAILABLE` para el vendedor:

  | Respuesta | Código |
  |---|---|
  | URL vacía o inválida, 401, 403 | `PHOTO_CLASSIFIER_NOT_CONFIGURED` |
  | 400, 415, 422 (imagen ilegible) | `PHOTO_CLASSIFIER_IMAGE_UNREADABLE` |
  | otro estado fuera de 2xx, red, tiempo agotado | `PHOTO_CLASSIFIER_UNAVAILABLE` |
  | JSON ilegible, sin `modelVersion` o sin predicción válida | `PHOTO_CLASSIFIER_INVALID_RESPONSE` |

  La imagen que no se pudo decodificar en Motoria también es `PHOTO_CLASSIFIER_IMAGE_UNREADABLE`.

## D4. Decisión por umbrales

`PhotoAnglePolicy.decide` (en `media/domain`):

| Condición | Resultado |
|---|---|
| toma predicha igual a la esperada y confianza ≥ `accept-threshold` (0.80) | `MATCH` |
| toma predicha distinta (otra toma u `OTHER`) y confianza ≥ `reject-threshold` (0.90) | `MISMATCH` |
| cualquier otro caso | `UNCERTAIN` |
| falla del clasificador | `UNAVAILABLE` (D3) |

El umbral de rechazo es más alto que el de aceptación: rechazar sin razón molesta más al vendedor que mandar una foto dudosa al analista. Los valores se ajustan con el conjunto de validación del modelo (puerta 0.1).

Con `motoria.media.angle-check.enabled=false`, o una toma que no está en `motoria.media.angle-check.slots`, el resultado es `NOT_CHECKED` y no se llama al clasificador.

## D5. Dónde vive el resultado

En la propia foto del anuncio (`listing.listing_photo`, V10), porque la regla de publicación, la bandeja del analista y la respuesta al portal son de `listing`:

- `angle_status` (`NOT_CHECKED`, `PENDING`, `MATCH`, `MISMATCH`, `UNCERTAIN`, `UNAVAILABLE`), `detected_slot`, `angle_confidence`, `angle_reason`, `angle_model_version`, `angle_latency_ms`, `angle_checked_at`;
- `read_vin`, `read_odometer_km`, `reading_anomaly`;
- `review_decision`, `review_reason`, `reviewed_by`, `reviewed_at`.

Reglas del dominio (`ListingPhoto`):

- adjuntar una foto, nueva o la misma otra vez, borra el resultado, las lecturas y la decisión anterior y deja `PENDING`. Volver a adjuntar la misma foto es el «Validar de nuevo» del portal para un `UNAVAILABLE`;
- `applyAngleCheck` solo guarda si la toma sigue teniendo la foto validada en `PENDING`: una respuesta tardía de una foto ya reemplazada se ignora;
- `angle_reason` de un `MISMATCH` es `NOT_THE_VEHICLE` si la toma predicha es `OTHER` y `WRONG_ANGLE` en otro caso; el de un `UNAVAILABLE` es el código del clasificador (solo para el analista);
- **por repetir** (`mustBeRepeated`): `MISMATCH` no aceptado por el analista, o rechazada por el analista;
- la respuesta del anuncio trae estos campos solo al dueño y al backoffice; el público ve solo la toma y la copia pública.

Las filas anteriores a V10 quedan `NOT_CHECKED`.

## D6. VIN y odómetro leídos de las fotos

YOLO clasifica, no lee texto. El contrato deja `vin` y `odometerKm` opcionales para cuando el servicio tenga OCR (detector de la región más OCR, puerta 0.3). La comparación ya está implementada y solo actúa cuando vienen.

`PhotoReadingPolicy` y `ListingPhotoReadingChecker`:

- foto `VIN`: el VIN leído, solo letras y dígitos y en mayúsculas, debe ser igual al atributo `VIN` del vehículo, que es `NumeroVinCarroceria` o, si falta, `NumeroChasis` de Credid (`add-consulta-credid-vehiculos`, D1);
- foto `DASHBOARD_ODOMETER`: el odómetro puede diferir del kilometraje declarado hasta el mayor entre `motoria.listing.photo.odometer-tolerance-km` (500) y `odometer-tolerance-percent` (5 %) del declarado;
- solo se compara si existen ambos valores. Como las fotos van antes de la consulta al registro, la comparación corre al guardar el resultado y otra vez en `ListingPublicationGuard`, al enviar a revisión y al publicar;
- una anomalía nueva se guarda en la foto (`VIN_MISMATCH` u `ODOMETER_MISMATCH`) y se publica **el evento existente** `vehicle.registry.anomaly.detected` desde `listing`, con el mismo payload que el de `vehicle` (`vehicleId`, `sellerId`, `anomalies`) más `listingId`. El consumidor de `notification` ya ignora campos desconocidos y prepara el aviso. No viajan el VIN, la placa ni el odómetro;
- se avisa una sola vez por foto: repetir la misma anomalía no publica otro evento;
- una anomalía **no bloquea** el envío («IA sugiere, humano decide»): el vendedor la ve traducida en la tarjeta de la foto y el analista la ve en la bandeja;
- limitación: si el guard rechaza el envío por otra regla, la transacción se revierte y la anomalía se registra en el intento siguiente.

## D7. Revisión del analista

- `GET /backoffice/photo-reviews` (BACKOFFICE y ADMIN): fotos de anuncios en `DRAFT` o `PENDING_REVIEW`, sin decisión, en `UNCERTAIN` o `UNAVAILABLE` o con una anomalía de lectura, las más antiguas primero. Excluye los anuncios del propio analista.
- `POST /backoffice/photo-reviews/{listingId}/{slot}` con `{"decision": "ACCEPTED" | "REJECTED", "reason": ...}`, auditado por `@Audited`:
  - `REJECTED` exige un motivo de `PhotoRejectionReason` (`WRONG_ANGLE`, `BLURRY`, `TOO_DARK`, `OBSTRUCTED`, `NOT_THE_VEHICLE`, `OTHER`): `LISTING_PHOTO_REVIEW_REASON_REQUIRED`;
  - se puede decidir sobre `UNCERTAIN`, `UNAVAILABLE`, `MISMATCH` (para corregir un falso rechazo) o una foto con anomalía; si no, `LISTING_PHOTO_NOT_REVIEWABLE`;
  - el analista no decide sobre un anuncio que él vende: `LISTING_PHOTO_SELF_REVIEW_FORBIDDEN` (403);
  - rechazar una foto de un anuncio `PENDING_REVIEW` lo devuelve a `DRAFT` (`Listing.returnToDraft`) y publica `listing.updated`, para que el vendedor pueda repetirla. Antes de este cambio un anuncio en revisión no tenía vuelta a borrador.
- `ListingPublicationPolicy` suma `LISTING_PHOTOS_TO_REPEAT`: no se envía ni se publica con una foto por repetir, sea obligatoria u opcional (el vendedor puede quitar la opcional). `UNCERTAIN`, `UNAVAILABLE` y las anomalías no bloquean. La firma anterior de `evaluate` se conserva.

## D8. Captura guiada en la web

Carpeta `apps/portal-cliente/src/pages/sell/capture/`:

- `GuidedCamera`: pantalla completa con `getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: 1920, height: 1080 } })`. El video usa `object-fit: cover` y la silueta un SVG con `preserveAspectRatio="xMidYMid slice"` y el mismo tamaño intrínseco, así coinciden. La foto es el cuadro completo en JPEG (calidad 0.92) y se sube con el flujo de siempre (URL prefirmada, confirmación, adjunto).
- `slotGuides`: por toma, la silueta (`front`, `rear`, `side`, `threeQuarterFront`, `threeQuarterRear`, `interior`, `dashboard`, `engine`, `tire`, `vin`), si va en espejo, el encuadre esperado normalizado, la fracción mínima que debe ocupar el vehículo y el punto de RA.
- `onDeviceDetector`: capa abstracta `OnDeviceDetector` con la implementación MediaPipe Tasks Vision (`ObjectDetector`, EfficientDet-Lite0, categorías `car` y `truck`):
  - se carga una sola vez y de forma perezosa con `import()` dinámico desde `VITE_MEDIAPIPE_BASE_URL` (por defecto `@mediapipe/tasks-vision@0.10.14` en jsDelivr, versión fija) y el modelo de `VITE_MEDIAPIPE_MODEL_URL`. No es dependencia npm: los tipos se declaran localmente;
  - corre cada 200 ms. Si la caja del vehículo queda dentro del encuadre (holgura 0.12) y ocupa al menos la fracción mínima durante 1,2 s, toma la foto sola. La captura automática se puede apagar y siempre está el botón manual;
  - **reemplazo futuro:** cuando exista el modelo local (D11), se exportará al navegador (ONNX con `onnxruntime-web`, o TF.js) y bastará otra implementación de `OnDeviceDetector`. No se implementa en este cambio.
- Solo silueta y guía, sin detección: interior delantero y trasero, tablero, motor, llantas y VIN.
- Caídas limpias: sin `mediaDevices` o sin contexto seguro, permiso negado o sin cámara, se muestra el motivo traducido y la opción de subir un archivo. Si MediaPipe no carga (red, CSP, sin WebAssembly o WebGL), queda la silueta con captura manual. El campo de archivo de F1 (`capture="environment"`) sigue en cada tarjeta.
- El estado de la validación de cada foto (`PhotoCheckStatus`): validando, correcta, repetir con motivo y la toma que parece ser, en revisión, aceptada por el analista, y la anomalía de lectura. «Continuar» queda deshabilitado mientras falten fotos o haya fotos por repetir.

## D9. Realidad aumentada con WebXR

- Solo con `VITE_AR_ENABLED=true` y si `navigator.xr.isSessionSupported('immersive-ar')` es verdadero en contexto seguro (en la práctica, Chrome en Android con ARCore). iOS Safari no tiene `immersive-ar`: allí no se ofrece.
- Sesión `immersive-ar` con `hit-test` obligatorio y `dom-overlay` opcional. El vendedor se para frente al vehículo, apunta al suelo bajo su centro y toca la pantalla: el frente del vehículo queda hacia donde está el vendedor. Se dibujan en el suelo los 8 puntos de toma exteriores (ángulo desde el frente hacia la derecha del vehículo y distancia de 3,2 a 3,8 m) y se resalta el de la toma actual. A 0,7 m del punto se le indica que llegó.
- WebXR no entrega la imagen de la cámara de forma estándar, así que la RA solo guía la posición: al tocar «Tomar la foto» termina la sesión y se abre la cámara guiada de D8.
- Cualquier error (sin WebGL, sin hit-test, permiso negado) muestra «La realidad aumentada no está disponible…» y la salida a la cámara guiada. Los tipos de WebXR se declaran localmente, sin dependencias.
- **RA nativa (ARKit, ARCore) fuera de este cambio:** se hará cuando exista app nativa.

## D10. CSP y Permissions-Policy

El repositorio no tiene la configuración del servidor web que entrega el portal (no hay nginx ni infraestructura versionados). `SecurityHeadersFilter` del backend solo cubre las respuestas de la API, no el HTML del portal. Por eso no se cambió ningún archivo de despliegue y queda como puerta 0.4. El servidor del portal debe enviar:

- `Permissions-Policy: camera=(self), xr-spatial-tracking=(self)`. Si hoy envía `camera=()`, la cámara queda bloqueada aunque el usuario la permita;
- en la CSP del portal:
  - `script-src 'self' https://cdn.jsdelivr.net 'wasm-unsafe-eval'` (el módulo y el WebAssembly de MediaPipe);
  - `connect-src` con la API, `https://cdn.jsdelivr.net` (archivos `.wasm`) y `https://storage.googleapis.com` (modelo);
  - `worker-src 'self' blob:` si la versión de MediaPipe usa workers;
  - `img-src` ya debe permitir `blob:` para las vistas previas;
  - `media-src 'self' blob: mediastream:` según el navegador.
- La cámara y WebXR exigen HTTPS.
- Alternativa más cerrada: copiar el paquete y el modelo de MediaPipe al mismo origen del portal y apuntar `VITE_MEDIAPIPE_BASE_URL` y `VITE_MEDIAPIPE_MODEL_URL` ahí; la CSP queda en `'self'` más `'wasm-unsafe-eval'`. Un `import()` dinámico no admite integridad (SRI): la versión fija y, mejor, el alojamiento propio reducen el riesgo de cadena de suministro.

## D11. Modelo local (fuera de este cambio; puertas 0.1 a 0.3)

El entrenamiento no es parte de este cambio. Lo que debe cumplir el modelo que se conecte por D3:

- **Tarea:** clasificación de la imagen completa por toma (por ejemplo YOLO11n-cls): 14 clases del protocolo más `OTHER` (no es el vehículo, foto en negro, pantalla, documento).
- **Dataset:**
  - fotos de anuncios de Motoria, ya etiquetadas por su toma, más las corregidas por el analista en la bandeja de este cambio;
  - base legal: el consentimiento «Mejora de modelos de IA» que ya existe en el perfil (Ley 8968). Solo fotos de vendedores con ese consentimiento; sin placas ni rostros visibles en lo que se conserve;
  - complemento con fotos propias o de aliados; nada de fotos de terceros sin licencia.
- **Etiquetado:** la toma declarada por el vendedor más la revisión humana; un conjunto dorado revisado dos veces para validación y prueba, separado por vehículo para evitar fugas.
- **Métricas mínimas** antes de producción (propuesta, a confirmar en la puerta): exactitud top-1 de al menos 95 % en exteriores y 90 % en interiores; tasa de falsos `MISMATCH` por debajo de 1 % con los umbrales de D4; matriz de confusión por par de tomas (las esquinas y laterales son las más parecidas); latencia p95 menor que 300 ms en la máquina del servicio.
- **Exportación:** ONNX para el servidor (ONNX Runtime) y, si se quiere detección en el dispositivo con el mismo modelo, ONNX o TF.js para el navegador (D8).
- **Servicio de inferencia:** HTTP propio con el contrato de D3. Sugerido en la VM de IA, que se une a producción por WireGuard: la URL apunta a la dirección del túnel y el puerto no se expone a internet. La versión del modelo viaja en cada respuesta.
- **OCR (puerta 0.3):** detector de la región del VIN y del odómetro más un OCR, en el mismo servicio. Hasta entonces `vin` y `odometerKm` llegan nulos y D6 no actúa.
- **ADVERTENCIA de licencia (puerta 0.2):** Ultralytics (YOLOv8, YOLO11) es **AGPL-3.0**. Servirlo por red en un producto comercial obliga a publicar el código fuente del servicio (y de lo que se considere obra derivada) o a comprar la licencia Enterprise de Ultralytics. Alternativas con licencia permisiva a evaluar en la puerta: clasificadores de `timm` o `torchvision` (Apache-2.0 o BSD, por ejemplo EfficientNet o MobileNetV3), RT-DETR en su implementación oficial (Apache-2.0) si hiciera falta detección, YOLOX (Apache-2.0). MediaPipe (D8) es Apache-2.0.

## D12. Privacidad

- Las fotos ya son del anuncio y del vendedor. El clasificador es de Motoria y corre en su infraestructura: ninguna imagen sale a un tercero.
- Se envía una copia reducida, recodificada en JPEG sin EXIF ni GPS. El original privado se lee con registro en la bitácora de acceso (`PHOTO_ANGLE_CHECK`).
- No se guarda la imagen en base64 ni la respuesta cruda del clasificador: solo la toma predicha, la confianza, la versión del modelo, la latencia y los valores leídos.
- El VIN leído y el odómetro solo se muestran al dueño (como anomalía) y al backoffice. El evento de anomalía no lleva VIN, placa ni odómetro.
- La cámara y la detección del navegador procesan el video en el dispositivo. Nada sale hasta que el vendedor sube la foto.
- Los logs llevan el id del anuncio o del medio y el código de error, nunca la toma enviada por el cliente ni la imagen.

## D13. Costo y configuración

No hay costo por llamada: por defecto se validan las 14 tomas (`motoria.media.angle-check.slots`, configurable). El costo es la VM del servicio de inferencia (puerta 0.1).

| Propiedad | Por defecto |
|---|---|
| `motoria.media.angle-check.enabled` | `true` (`MOTORIA_PHOTO_ANGLE_CHECK_ENABLED`) |
| `motoria.media.angle-check.provider` | `stub` (`MOTORIA_PHOTO_CLASSIFIER_PROVIDER`) |
| `motoria.media.angle-check.url` | vacía (`MOTORIA_PHOTO_CLASSIFIER_URL`) |
| `motoria.media.angle-check.token` | vacío, opcional |
| `motoria.media.angle-check.timeout` | `PT5S` |
| `motoria.media.angle-check.slots` | las 14 tomas |
| `motoria.media.angle-check.accept-threshold` | `0.80` |
| `motoria.media.angle-check.reject-threshold` | `0.90` |
| `motoria.media.angle-check.max-side` | `1024` |
| `motoria.listing.photo.odometer-tolerance-km` | `500` |
| `motoria.listing.photo.odometer-tolerance-percent` | `5` |

Con el proveedor `http` y la URL vacía, cada foto queda `UNAVAILABLE` con `PHOTO_CLASSIFIER_NOT_CONFIGURED`: no bloquea y va al analista.

## D14. Migración V10

`V10__listing_photo_angle_check.sql`, en el rango de F1 (V1 a V19). V9 es la consulta del vehículo por placa (`add-consulta-credid-vehiculos`); F2 usa V20 a V39. Revisado: ninguna otra rama ni worktree usa V10.

- Agrega a `listing.listing_photo` las columnas de D5. La tabla ya tiene las columnas de auditoría (`created_at`, `created_by`, `updated_at`, `updated_by`) desde V6.
- `CHECK` de cada enumeración (`angle_status`, `reading_anomaly`, `review_decision`, `review_reason`), de la confianza entre 0 y 1 y del odómetro no negativo.
- `angle_status` es `NOT NULL DEFAULT 'NOT_CHECKED'`, así que las filas previas no necesitan un `UPDATE`.
- Índice parcial `ix_listing_photo_angle_review (updated_at) WHERE review_decision IS NULL AND (angle_status IN ('UNCERTAIN', 'UNAVAILABLE') OR reading_anomaly IS NOT NULL)` para la bandeja.
