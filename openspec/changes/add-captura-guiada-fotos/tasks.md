# Tasks

Marcas: (A) agente, (B) puerta del dueño, (C) calendario no acelerable. Una marca [x] significa «escrito», nunca «compilado ni probado»; eso lo confirma el CI. Migración: **V10**, dentro del rango de F1 (V1 a V19).

Rutas de evidencia relativas a `backend/src/main/java/ai/motoria/` (código), `backend/src/main/resources/` (migraciones y configuración), `backend/src/test/java/ai/motoria/` (pruebas) y `frontend/` (web).

## 0. Puertas

- [ ] 0.1 (B) Entrenar y servir el modelo local de ángulo (design D11):
  - dataset a partir de fotos de anuncios con el consentimiento «Mejora de modelos de IA» y fotos propias o de aliados;
  - etiquetado (toma declarada más revisión humana) y conjunto dorado separado por vehículo;
  - métricas mínimas acordadas (exactitud por toma, falsos `MISMATCH`, latencia p95) y ajuste de `accept-threshold` y `reject-threshold`;
  - exportación (ONNX) y servicio de inferencia con el contrato de design D3, en la VM de IA por WireGuard;
  - configurar `MOTORIA_PHOTO_CLASSIFIER_PROVIDER=http` y `MOTORIA_PHOTO_CLASSIFIER_URL`. Hasta entonces se usa el `stub`, que siempre responde la toma esperada;
  - el servicio de inferencia y los scripts de entrenamiento están escritos en `add-clasificador-fotos-yolo` (`ml/photo-classifier/`), sin entrenar ni desplegar: sus puertas 0.1 a 0.3 cierran esta.
- [ ] 0.2 (B) Licencia: Ultralytics es AGPL-3.0. Decidir entre publicar el código del servicio de inferencia, comprar la licencia Enterprise o usar una alternativa permisiva (timm o torchvision, YOLOX, RT-DETR) (design D11).
  - **Decidido el 2026-11-27:** durante el desarrollo se usa Ultralytics YOLO con la licencia gratuita AGPL-3.0. Mientras rija, el código del servicio de inferencia (`ml/photo-classifier/`) debe estar disponible para quien interactúe con él por red (`ml/photo-classifier/LICENSE-NOTICE.md`; design D2 de `add-clasificador-fotos-yolo`).
  - **Sigue abierta:** la licencia Enterprise de Ultralytics se negociará y firmará antes del lanzamiento (puerta 0.4 de `add-clasificador-fotos-yolo`).
- [ ] 0.3 (B) OCR del VIN y del odómetro (detector de la región más OCR) en el mismo servicio. Sin él, `vin` y `odometerKm` llegan nulos y la comparación de design D6 no actúa.
- [ ] 0.4 (B) CSP y Permissions-Policy del servidor que entrega el portal (no está en el repositorio): `camera` y `xr-spatial-tracking` para el propio origen, `script-src` y `connect-src` del CDN de MediaPipe y del modelo, `wasm-unsafe-eval`, o alojar MediaPipe en el mismo origen (design D10).
- [ ] 0.5 (B) Prueba en dispositivos reales: iPhone con Safari (sin RA, cámara y silueta), Android de gama media con Chrome (MediaPipe y RA con `VITE_AR_ENABLED=true`), permiso negado, sin cámara y conexión lenta.
- [ ] 0.6 (B) Canal de envío de las notificaciones (correo o push): hoy `NotificationService` solo registra el aviso de anomalía en el log, como el resto de F1 (puerta 0.5 de `add-consulta-credid-vehiculos`).

## 1. Backend: media

- [x] 1.1 (A) Puerto del clasificador local, adaptadores y productor (design D3). Evidencia: `media/integration/PhotoAngleClassifierPort.java`, `PhotoAngleClassification.java`, `PhotoAngleClassifierUnavailableException.java`, `StubPhotoAngleClassifierAdapter.java`, `HttpPhotoAngleClassifierAdapter.java` (`parse`, `requestBody`) y `PhotoAngleClassifierPortProducer.java`.
- [x] 1.2 (A) Decisión por umbrales (design D4). Evidencia: `media/domain/PhotoAnglePolicy.java`.
- [x] 1.3 (A) Validación sin transacción alrededor de la llamada: lectura en transacción corta con bitácora, reducción a JPEG sin metadatos, `NOT_CHECKED` y `UNAVAILABLE` (design D2 y D12). Evidencia: `media/service/PhotoAngleImageReader.java`, `media/service/PhotoAngleCheckService.java` y `media/service/MediaPortAdapter.java` (`checkPhotoAngle`, sin `@Transactional`).
- [x] 1.4 (A) Puerto compartido. Evidencia: `common/port/PhotoAngleCheckResult.java` y `common/port/MediaPort.java` (`checkPhotoAngle`).
- [x] 1.5 (A) Códigos estables. Evidencia: `media/exception/MediaErrorCodes.java` (`PHOTO_CLASSIFIER_NOT_CONFIGURED`, `PHOTO_CLASSIFIER_UNAVAILABLE`, `PHOTO_CLASSIFIER_INVALID_RESPONSE`, `PHOTO_CLASSIFIER_IMAGE_UNREADABLE`).

## 2. Backend: listing y notification

- [x] 2.1 (A) Migración V10 (design D5 y D14). Evidencia: `db/migration/V10__listing_photo_angle_check.sql`.
- [x] 2.2 (A) Dominio de la foto (design D5 y D6). Evidencia:
  - `listing/domain/PhotoAngleStatus.java`, `PhotoReviewDecision.java`, `PhotoRejectionReason.java`, `PhotoAnomaly.java`, `PhotoReadingPolicy.java`;
  - `listing/domain/ListingPhoto.java` (`replace`, `applyAngleCheck`, `recordReadingAnomaly`, `isReviewable`, `awaitsAnalyst`, `review`, `mustBeRepeated`, `repeatReason`);
  - `listing/domain/Listing.java` (`photoSlotsToRepeat`, `returnToDraft`).
- [x] 2.3 (A) Adjunto, validación y resultado en la misma solicitud (design D2). Evidencia: `listing/service/ListingPhotoService.java` (`attach` sin transacción), `listing/service/ListingPhotoAngleApplier.java` (`attach`, `apply`, `reasonOf`, `editableListing`).
- [x] 2.4 (A) Lecturas de VIN y odómetro, anomalías y evento existente (design D6). Evidencia: `listing/service/ListingPhotoReadingChecker.java`, `listing/event/ListingPhotoAnomalyPayload.java`, `listing/event/ListingEventProducer.java` (`photoAnomalyDetected`) y `application.properties` (`motoria.listing.photo.odometer-tolerance-*`).
- [x] 2.5 (A) Regla de publicación (design D7). Evidencia: `listing/domain/ListingPublicationPolicy.java` (`PHOTOS_TO_REPEAT`, `evaluate` con `slotsToRepeat`, firma anterior conservada) y `listing/service/ListingPublicationGuard.java` (`readingChecker.check`, `photoSlotsToRepeat`).
- [x] 2.6 (A) Estado de la validación en la respuesta del anuncio, solo para el dueño y el backoffice (design D5). Evidencia: `listing/dto/ListingPhotoResponse.java` y `listing/mapper/ListingMapper.java` (`toPhotoResponse(photo, privileged)`).
- [x] 2.7 (A) Bandeja y decisión del analista, BACKOFFICE y ADMIN, auditada, sin autorrevisión (design D7). Evidencia: `listing/rest/BackofficePhotoReviewResource.java`, `listing/service/ListingPhotoReviewService.java`, `listing/repository/ListingPhotoRepository.java` (`findAwaitingAnalyst`), `listing/dto/PhotoReviewItemResponse.java`, `listing/dto/PhotoReviewRequest.java` y `listing/mapper/ListingMapper.java` (`toReviewItem`).
- [x] 2.8 (A) Códigos de error. Evidencia: `listing/exception/ListingErrorCodes.java` (`LISTING_PHOTO_NOT_REVIEWABLE`, `LISTING_PHOTO_REVIEW_REASON_REQUIRED`, `LISTING_PHOTO_SELF_REVIEW_FORBIDDEN`) y `ListingPublicationPolicy.PHOTOS_TO_REPEAT` (`LISTING_PHOTOS_TO_REPEAT`).
- [x] 2.9 (A) Configuración (design D13). Evidencia: `application.properties` (`motoria.media.angle-check.*`) y `backend/src/test/resources/application.properties` (`motoria.media.angle-check.provider=stub`).
- [x] 2.10 (A) Notificación de las anomalías nuevas por el consumidor existente (sin cambio de lógica). Evidencia: `notification/service/NotificationService.java` y `notification/dto/VehicleRegistryAnomalyNotificationCommand.java` (documentación).

## 3. Frontend

- [x] 3.1 (A) Tipos y hooks. Evidencia: `packages/api/src/types.ts` (`PHOTO_ANGLE_STATUSES`, `PHOTO_REVIEW_DECISIONS`, `PHOTO_REJECTION_REASONS`, `PHOTO_ANOMALIES`, campos nuevos de `ListingPhoto`, `PhotoReviewItem`, `PhotoReviewRequest`) y `packages/api/src/hooks.ts` (`usePhotoReviews`, `useReviewPhoto`).
- [x] 3.2 (A) Cámara guiada con silueta, detección en el dispositivo detrás de `OnDeviceDetector` (MediaPipe por CDN, sin npm), captura automática y manual, y caídas limpias (design D8). Evidencia: `apps/portal-cliente/src/pages/sell/capture/GuidedCamera.tsx`, `onDeviceDetector.ts`, `slotGuides.ts`, `Silhouette.tsx`; `apps/portal-cliente/src/config.ts` (`captureConfig`) y `vite-env.d.ts`.
- [x] 3.3 (A) Realidad aumentada WebXR con detección de capacidad y bandera (design D9). Evidencia: `apps/portal-cliente/src/pages/sell/capture/webxr.ts` (`isImmersiveArSupported`, `startArGuide`, `shotPosition`) y `ArGuide.tsx`.
- [x] 3.4 (A) Paso de fotos con captura guiada y estado de validación por foto (validando, correcta, repetir con motivo, en revisión, anomalía); «Continuar» bloqueado con fotos por repetir. Evidencia: `apps/portal-cliente/src/pages/sell/PhotosStep.tsx` y `capture/PhotoCheckStatus.tsx`.
- [x] 3.5 (A) Backoffice: bandeja «Ángulo de las fotos», ruta y menú. Evidencia: `apps/backoffice/src/pages/PhotoReviewsPage.tsx` y `apps/backoffice/src/App.tsx`.
- [x] 3.6 (A) Estilos. Evidencia: `packages/ui/src/styles.css` (`m-capture*`, `m-ar*`).
- [x] 3.7 (A) Textos en español e inglés con paridad de claves. Evidencia: `packages/i18n/src/locales/es.ts` y `en.ts`:
  - `photoAngle.*` (estados, motivos, anomalías, decisión del analista);
  - `capture.*` (cámara, permisos, estados, RA no disponible);
  - `sell.photos.tipGuide` y `sell.photos.repeatTitle*`;
  - `backoffice.nav.photoReviews` y `backoffice.photoReviews.*`;
  - `errors.LISTING_PHOTOS_TO_REPEAT`, `errors.LISTING_PHOTO_*` y `errors.PHOTO_CLASSIFIER_*`.
- [ ] 3.8 (B) Configuración de despliegue de CSP y Permissions-Policy: depende de la puerta 0.4 (no hay archivos de servidor del portal en el repositorio).

## 4. Pruebas (escritas, no ejecutadas)

- [x] 4.1 (A) Lectura de la respuesta del servicio de inferencia: mejor predicción, predicciones inválidas, lecturas válidas e inválidas, estados 400, 401, 403, 500 y 503, JSON ilegible, sin versión, sin predicciones, URL sin configurar y cuerpo de la solicitud. Evidencia: `media/integration/HttpPhotoAngleClassifierAdapterTest.java`.
- [x] 4.2 (A) Stub determinista. Evidencia: `media/integration/StubPhotoAngleClassifierAdapterTest.java`.
- [x] 4.3 (A) Reglas de decisión por umbrales. Evidencia: `media/domain/PhotoAnglePolicyTest.java`.
- [x] 4.4 (A) Validación en `media`: toma fuera de la lista y validación apagada, reducción y envío como JPEG, `MISMATCH`, lecturas, falla del clasificador, error inesperado, imagen ilegible y medio inexistente. Evidencia: `media/service/PhotoAngleCheckServiceTest.java`.
- [x] 4.5 (A) Comparación de VIN y odómetro. Evidencia: `listing/domain/PhotoReadingPolicyTest.java`.
- [x] 4.6 (A) Dominio de la foto: `PENDING` al adjuntar, resultado solo para la foto vigente, `MISMATCH` por repetir, `UNCERTAIN` y `UNAVAILABLE` al analista, aceptación y rechazo, anomalía una sola vez, reemplazo que limpia todo, fotos por repetir y vuelta a borrador. Evidencia: `listing/domain/ListingPhotoTest.java`.
- [x] 4.7 (A) Regla de publicación con fotos por repetir. Evidencia: `listing/domain/ListingPublicationPolicyTest.java` (`photoToRepeatShouldBlockReview`, `noPhotoToRepeatShouldKeepThePreviousRules`).
- [x] 4.8 (A) Orquestación del adjunto (orden attach, validación, resultado; falla inesperada como `UNAVAILABLE`; foto inválida sin llamar al clasificador). Evidencia: `listing/service/ListingPhotoServiceTest.java`.
- [x] 4.9 (A) Guardado del resultado: motivos `WRONG_ANGLE` y `NOT_THE_VEHICLE`, `UNAVAILABLE` con su código, `NOT_CHECKED`, resultado de una foto reemplazada y comparación de lecturas. Evidencia: `listing/service/ListingPhotoAngleApplierTest.java`.
- [x] 4.10 (A) Lecturas y aviso una sola vez, VIN a la espera del registro, odómetro dentro y fuera de la tolerancia. Evidencia: `listing/service/ListingPhotoReadingCheckerTest.java`.
- [x] 4.11 (A) Decisión del analista: aceptar, rechazar con vuelta a borrador, motivo obligatorio, autorrevisión, foto sin validación pendiente y anuncio publicado. Evidencia: `listing/service/ListingPhotoReviewServiceTest.java`.
- [x] 4.12 (A) Recurso REST: bandeja del analista, decisión del ADMIN, decisión obligatoria (400), autorrevisión (403), vendedor (403) y anónimo (401). Evidencia: `listing/rest/BackofficePhotoReviewResourceTest.java`.
- [x] 4.13 (A) Consumidor de notificación con `VIN_MISMATCH` y `ODOMETER_MISMATCH` publicados por `listing`. Evidencia: `notification/event/NotificationEventConsumerTest.java` (`shouldDispatchPhotoReadingAnomalyFromTheListingModule`).
- [ ] 4.14 (A) Prueba de integración contra PostgreSQL de V10 y de la consulta de la bandeja: pendiente; el proyecto aún no tiene pruebas con base de datos.
- [ ] 4.15 (A) Pruebas del portal (cámara guiada, RA, estado de validación): pendiente; el frontend aún no tiene pruebas.

## 5. Fuera de este cambio

- Entrenamiento del modelo y servicio de inferencia (puertas 0.1 y 0.2).
- OCR del VIN y del odómetro (puerta 0.3).
- Modelo local exportado al navegador en lugar de MediaPipe (design D8).
- RA nativa (ARKit, ARCore): cuando exista app nativa.
- Validación asíncrona por evento (design D2), si la latencia medida lo pide.
