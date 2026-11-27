## ADDED Requirements

### Requirement: Captura guiada de cada toma del protocolo
El sistema DEBE (SHALL) ofrecer en el paso de fotos del portal una cámara trasera con la silueta de la toma superpuesta y su instrucción traducida, detectar el vehículo en el dispositivo en las tomas exteriores y tomar la foto automáticamente cuando el vehículo queda dentro de la silueta de forma estable, con botón manual siempre disponible. NO DEBE (MUST NOT) impedir que el vendedor suba un archivo cuando no hay cámara, se niega el permiso o la detección no está disponible.

#### Scenario: Toma exterior con detección
- **WHEN** el vendedor abre «Tomar con guía» en la toma `FRONT` y el detector del dispositivo ve un `car` o `truck` dentro del encuadre de la silueta, ocupando la fracción mínima, durante 1,2 segundos
- **THEN** el portal toma la foto en JPEG, la sube con el flujo de carga existente y la adjunta a la toma

#### Scenario: Tomas sin detección
- **WHEN** el vendedor abre la cámara guiada en `INTERIOR_FRONT`, `INTERIOR_REAR`, `DASHBOARD_ODOMETER`, `ENGINE`, `TIRES` o `VIN`
- **THEN** se muestran la silueta y la instrucción, no se carga el detector y la foto se toma con el botón manual

#### Scenario: Sin detector
- **WHEN** MediaPipe no carga desde `VITE_MEDIAPIPE_BASE_URL` (sin red, bloqueado por la CSP o sin WebAssembly)
- **THEN** la cámara sigue con la silueta, se avisa que la detección automática no está disponible y la foto se toma a mano

#### Scenario: Permiso negado o sin cámara
- **WHEN** el navegador niega el permiso de cámara, no hay cámara o el contexto no es seguro
- **THEN** se muestra el motivo traducido con la opción «Subir un archivo» y el campo de archivo de la tarjeta sigue disponible

### Requirement: Guía de puntos de toma con realidad aumentada
El sistema DEBE (SHALL) ofrecer, solo con la bandera `VITE_AR_ENABLED` y cuando el navegador soporta WebXR `immersive-ar` con `hit-test`, una guía que marca en el suelo los puntos de las 8 tomas exteriores alrededor del vehículo y resalta el de la toma actual. NO DEBE (MUST NOT) ofrecerla en navegadores sin ese soporte y DEBE (MUST) volver a la cámara guiada ante cualquier error.

#### Scenario: Android con WebXR
- **WHEN** el vendedor inicia la guía de RA en `REAR_LEFT`, marca el centro del vehículo tocando el suelo frente a él y camina hasta el punto resaltado
- **THEN** el portal le indica que llegó y, al tocar «Tomar la foto», cierra la sesión de RA y abre la cámara guiada de esa toma

#### Scenario: iPhone sin WebXR
- **WHEN** `navigator.xr` no existe o `isSessionSupported('immersive-ar')` es falso
- **THEN** el botón de realidad aumentada no aparece y la cámara guiada funciona igual

#### Scenario: Falla al iniciar
- **WHEN** la sesión de RA no puede iniciar (sin WebGL, sin hit-test o permiso negado)
- **THEN** se muestra «La realidad aumentada no está disponible en este dispositivo» con la salida a la cámara guiada

### Requirement: Validación del ángulo de cada foto con un modelo local
El sistema DEBE (SHALL) validar, al adjuntar la foto de una toma configurada en `motoria.media.angle-check.slots`, que la foto corresponde a la toma con el clasificador local de Motoria, y responder en la misma solicitud con `MATCH`, `MISMATCH` (con la toma detectada), `UNCERTAIN` o `UNAVAILABLE`. NO DEBE (MUST NOT) llamar al clasificador dentro de una transacción de base de datos, enviar la imagen a un proveedor externo, ni guardar la imagen o la respuesta cruda del clasificador.

#### Scenario: Foto correcta
- **WHEN** el clasificador predice la toma esperada con confianza mayor o igual que `accept-threshold`
- **THEN** la foto queda `MATCH` con la versión del modelo, la confianza y la latencia, y el portal muestra «Foto correcta»

#### Scenario: Foto de otra toma
- **WHEN** el clasificador predice otra toma o `OTHER` con confianza mayor o igual que `reject-threshold`
- **THEN** la foto queda `MISMATCH` con `detectedSlot`, el motivo `WRONG_ANGLE` (o `NOT_THE_VEHICLE` si es `OTHER`) y el portal pide repetirla con el motivo traducido

#### Scenario: Resultado dudoso
- **WHEN** la confianza no alcanza ninguno de los dos umbrales
- **THEN** la foto queda `UNCERTAIN`, no bloquea el envío y aparece en la bandeja del analista

#### Scenario: Clasificador no disponible
- **WHEN** el servicio de inferencia no está configurado, no responde a tiempo o responde algo inválido
- **THEN** la foto queda `UNAVAILABLE` con el código `PHOTO_CLASSIFIER_*`, el adjunto responde 200, no se bloquea el envío, el portal ofrece «Validar de nuevo» y la foto aparece en la bandeja del analista

#### Scenario: Foto reemplazada durante la validación
- **WHEN** llega el resultado de una foto que ya no es la de la toma
- **THEN** el resultado se ignora y la foto nueva conserva su propia validación

#### Scenario: Toma no configurada
- **WHEN** la toma no está en `motoria.media.angle-check.slots` o `motoria.media.angle-check.enabled` es falso
- **THEN** la foto queda `NOT_CHECKED` y no se llama al clasificador

### Requirement: VIN y odómetro leídos de las fotos
El sistema DEBE (SHALL), cuando el servicio de inferencia devuelve el VIN o el odómetro leídos, comparar el VIN de la foto `VIN` con el VIN o número de chasis del registro y el odómetro de la foto `DASHBOARD_ODOMETER` con el kilometraje declarado (tolerancia configurable), registrar la anomalía `VIN_MISMATCH` u `ODOMETER_MISMATCH` en la foto y publicar `vehicle.registry.anomaly.detected` para avisar al vendedor. Una anomalía NO DEBE (MUST NOT) bloquear el envío a revisión.

#### Scenario: VIN distinto al del registro
- **WHEN** el VIN leído, sin separadores, no es igual al atributo `VIN` del vehículo verificado
- **THEN** la foto tiene `readingAnomaly = VIN_MISMATCH`, el portal la muestra traducida, se publica el evento con el vendedor y el código (sin VIN ni placa) y el analista la ve en la bandeja

#### Scenario: Odómetro fuera de tolerancia
- **WHEN** el odómetro leído difiere del kilometraje declarado más que el mayor entre `odometer-tolerance-km` y `odometer-tolerance-percent` del declarado
- **THEN** la foto tiene `readingAnomaly = ODOMETER_MISMATCH` y se avisa al vendedor una sola vez

#### Scenario: Registro aún no consultado
- **WHEN** la foto del VIN se valida antes de la consulta al registro
- **THEN** no se compara; la comparación se hace al enviar el anuncio a revisión, cuando el vehículo ya tiene su VIN

#### Scenario: Servicio sin OCR
- **WHEN** el servicio de inferencia no devuelve `vin` ni `odometerKm`
- **THEN** no se registra ninguna anomalía de lectura

### Requirement: Revisión del ángulo por el analista
El sistema DEBE (SHALL) ofrecer a BACKOFFICE y ADMIN una bandeja con las fotos `UNCERTAIN`, `UNAVAILABLE` o con anomalía de lectura sin decisión, y una acción auditada para aceptarlas o rechazarlas con un motivo traducible. El analista NO DEBE (MUST NOT) decidir sobre las fotos de un anuncio que él vende.

#### Scenario: Aceptar una foto dudosa
- **WHEN** el analista acepta una foto `UNCERTAIN`
- **THEN** la foto queda `ACCEPTED`, sale de la bandeja y el portal muestra «Aceptada por un analista»

#### Scenario: Rechazar una foto de un anuncio en revisión
- **WHEN** el analista rechaza con motivo `BLURRY` una foto de un anuncio `PENDING_REVIEW`
- **THEN** la foto queda por repetir con el motivo traducido, el anuncio vuelve a `DRAFT` y se publica `listing.updated`

#### Scenario: Rechazo sin motivo
- **WHEN** el analista rechaza sin indicar el motivo
- **THEN** el servidor responde `LISTING_PHOTO_REVIEW_REASON_REQUIRED`

#### Scenario: Autorrevisión
- **WHEN** el analista es el vendedor del anuncio
- **THEN** el servidor responde 403 con `LISTING_PHOTO_SELF_REVIEW_FORBIDDEN`

#### Scenario: Sin permiso
- **WHEN** un usuario SELLER o anónimo llama la bandeja o la acción
- **THEN** el servidor responde 403 o 401

### Requirement: Fotos por repetir bloquean la publicación
El sistema DEBE (SHALL) rechazar el envío a revisión y la publicación de un anuncio que tenga alguna foto en `MISMATCH` no aceptada por el analista o rechazada por el analista, con el código `LISTING_PHOTOS_TO_REPEAT`. Las fotos `UNCERTAIN`, `UNAVAILABLE` o con anomalía de lectura NO DEBEN (MUST NOT) bloquearlo.

#### Scenario: Foto por repetir
- **WHEN** el vendedor envía a revisión un anuncio con la foto `VIN` en `MISMATCH`
- **THEN** el servidor rechaza el envío con `LISTING_PHOTOS_TO_REPEAT` y el portal mantiene deshabilitado «Continuar» en el paso de fotos

#### Scenario: Foto en revisión del analista
- **WHEN** el anuncio solo tiene fotos `MATCH`, `UNCERTAIN` o `UNAVAILABLE` y cumple las demás reglas
- **THEN** el anuncio pasa a `PENDING_REVIEW` y las fotos dudosas quedan en la bandeja del analista
