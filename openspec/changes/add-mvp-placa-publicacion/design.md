# Design: MVP placa y publicación (F1)

## D1. Flujo del vendedor

1. Registro e inicio de sesión con Keycloak (pantalla de registro de Keycloak con el tema de Motoria; rol `SELLER` asignado por defecto a las cuentas nuevas; roles en `realm_access.roles`). Para publicar se exige identidad nivel 2 (documento y prueba de vida). Mientras no haya proveedor (tarea 1.1 de la base), la vía manual es: el vendedor sube foto del documento por ambos lados y una selfie sosteniendo el documento junto a un código de un solo uso que muestra la pantalla; un analista `BACKOFFICE` compara rostro, documento y código y aprueba o rechaza, con registro del analista y la evidencia. Esta vía tiene un cupo diario configurable; si se agota, la publicación espera.
2. El vendedor escribe la placa. Si existe un `Vehicle` con datos registrales vigentes, se reutiliza; si no, se crea en `REGISTRY_PENDING`.
3. El vendedor sube el informe registral del vehículo comprado en rnpdigital.com (PDF). Opción "que Motoria lo obtenga": un operador de backoffice lo compra y lo sube; el costo va en configuración.
4. La IA extrae los campos con su confianza y la página de origen. Si la confianza de algún campo obligatorio está bajo el umbral, si el documento es más antiguo que el máximo configurado o si la placa del documento no coincide, el caso va a la bandeja del backoffice (`vehicle.registry.review.required`).
5. Significado de los eventos: `vehicle.registry.requested` al recibir el documento; `vehicle.registry.review.required` cuando la extracción termina y el caso va al analista; `vehicle.registry.completed` solo cuando el vehículo llega a `REGISTRY_VERIFIED`, sea por aprobación automática (cuando se habilite) o por el analista. Con los datos confirmados, el vehículo pasa a `REGISTRY_VERIFIED`, se calculan los bloqueos (gravámenes, anotaciones) y se compara el propietario con la cédula verificada del vendedor.
6. Se crea el anuncio prellenado; el vendedor completa los datos declarados con ayuda del asistente, sube las fotos del protocolo y lo envía a revisión.
7. Un analista aprueba y publica (flujo existente `submit-review` y `publish`).

## D2. Migraciones

- Se agrega `quarkus-flyway`, `quarkus.hibernate-orm.database.generation=none` y `quarkus.flyway.migrate-at-start=true` con `baseline-on-migrate=true`.
- V1 crea los esquemas y tablas existentes (`listing.listing` y `outbox_event`, esta en el esquema por defecto) tal como los mapean hoy las entidades, con `IF NOT EXISTS`, para que entornos ya creados con `update` tomen V1 como línea base sin perder datos.
- Numeración reservada para F1: V1 a V19. Un archivo por módulo y paso: V2 `user`, V3 `media`, V4 `vehicle`, V5 `ai`, V6 cambios en `listing`. `media` va antes que `vehicle` porque la evidencia de los datos registrales referencia `media.media_object`. Entre esquemas de módulos distintos no se crean claves foráneas (los módulos no se importan entre sí); la referencia es un UUID validado por el servicio.
- Columnas de auditoría en toda tabla nueva: `created_at`, `created_by`, `updated_at`, `updated_by`.
- No hay multi-tenant; el aislamiento es por usuario y rol, con verificación de propiedad en cada servicio.

## D3. Modelo de datos

- Esquema del módulo `user`: `usr` (como fija BACKEND_ARCHITECTURE.md; `user` es palabra reservada en PostgreSQL).
- `usr.user_profile`: id (claim `sub` de Keycloak), nombre, cédula y tipo (cifrados en reposo), teléfono, correo, país, `identity_level` (`LEVEL_1`, `LEVEL_2`, `LEVEL_3`), proveedor y referencia de la verificación.
- `usr.consent`: usuario, tipo (`DATA_PROCESSING`, `SHARE_INSURANCE_BROKER`, `SHARE_BANK`, `PHOTO_LOCATION`, `MODEL_TRAINING`), versión del texto, otorgado o revocado, fecha, IP.
- `vehicle.vehicle`: id, `country_code`, `plate` (normalizada), `status` (`REGISTRY_PENDING`, `REGISTRY_IN_REVIEW`, `REGISTRY_VERIFIED`, `REGISTRY_REJECTED`), `transfer_blocked` y motivo, `registry_checked_at`. Único por (`country_code`, `plate`).
- `vehicle.vehicle_attribute`: vehículo, clave (`BRAND`, `MODEL`, `STYLE`, `YEAR`, `COLOR`, `VIN`, `ENGINE_NUMBER`, `DISPLACEMENT_CC`, `FUEL`, `DRIVETRAIN`, `DOORS`, `CAPACITY`, `OWNER_ID_HASH`, `OWNER_NAME`), valor, `source` (`REGISTRY_DOCUMENT`, `REGISTRY_AGREEMENT`, `SELLER_DECLARED`, `ANALYST`), evidencia (id de media y página), confianza, fecha. El propietario se guarda como hash de la cédula más el nombre, nunca la cédula en claro.
- `vehicle.registry_encumbrance`: vehículo, tipo (`PLEDGE`, `SEIZURE`, `ANNOTATION`, `COLLISION`, `OTHER`), descripción, si bloquea el traspaso, evidencia.
- `vehicle.registry_discrepancy`: vehículo, clave, valor registral, valor declarado, estado.
- `media.media_object`: id, dueño, tipo (`REGISTRY_DOCUMENT`, `LISTING_PHOTO`, `IDENTITY_DOCUMENT` y `REGISTRY_PAGE_IMAGE`, este último creado solo por el servidor al renderizar las páginas del informe para el analista), `photo_slot` del protocolo, clave S3, `content_type`, tamaño, `sha256`, ancho y alto, metadatos de captura, visibilidad (`PRIVATE`, `PUBLIC`).
- `media.photo_protocol_slot`: país, código (`FRONT`, `REAR`, `LEFT`, `RIGHT`, `FRONT_LEFT`, `FRONT_RIGHT`, `REAR_LEFT`, `REAR_RIGHT`, `INTERIOR_FRONT`, `INTERIOR_REAR`, `DASHBOARD_ODOMETER`, `ENGINE`, `TIRES`, `VIN`), obligatorio, orden, clave i18n de la instrucción. Se siembra en la migración y se edita desde admin.
- `ai.ai_inference`: id, caso de uso (`REGISTRY_EXTRACTION`, `LISTING_COPY`), proveedor, modelo, versión de prompt, referencias de entrada, salida JSON, confianza, explicación, latencia, costo, revisor, decisión humana, `correlation_id`.
- `listing.listing`: se agrega `vehicle_id` (reemplaza a `vehicle_spec_id`, que se migra y se deja de usar), `province`, `transmission`, `trust_level`, y los datos declarados (`declared_equipment`, `maintenance_history`, `negotiable`).

## D4. Lectura del informe registral

- Puerto `ai.integration.LlmPort` con un adaptador por proveedor elegido por configuración (`motoria.ai.provider`, `motoria.ai.model`); la llave va por variable de entorno y nunca en el repo.
- Entrada: el PDF (texto extraído; si es imagen, la página como imagen) y un esquema JSON de salida con los campos de D3 más gravámenes, fecha del documento y placa del documento.
- Controles: la salida se valida contra el esquema; todo valor debe citar la página; un campo sin cita se descarta; el texto del documento se trata como dato y nunca como instrucción (OWASP LLM01); temperatura 0.
- Conjunto de prueba: 20 informes reales enmascarados entregados por el dueño (tarea 0.3) para medir precisión por campo antes de activar la aprobación automática. Mientras no se mida, todo documento pasa por el analista.

## D5. Seguridad de archivos

- Tipos permitidos por lista blanca y verificación de la firma del archivo, no de la extensión: PDF para documentos; JPEG, PNG y HEIC para fotos. Tamaño máximo configurable.
- Carga en dos pasos: el backend emite una URL prefirmada de subida con tipo y tamaño fijados, y confirma después leyendo el objeto. Las claves S3 se generan en el servidor.
- Los PDF no se renderizan en el navegador de terceros; la vista del analista usa una imagen generada en el servidor.
- Las fotos públicas se publican sin metadatos EXIF; el original con metadatos queda privado.
- Los documentos de identidad y registrales son privados, con URL de lectura de corta duración y acceso auditado.

## D6. Frontend

- Monorepo `frontend/` con `portal-cliente` y `backoffice` sobre Vite, React, TypeScript, React Hook Form, TanStack Query, react-i18next (español e inglés desde el inicio) y un paquete compartido `ui`.
- Portal cliente: inicio y búsqueda, ficha del anuncio, flujo "vender" (placa, informe, revisión de la ficha, datos declarados, fotos, envío) y "mis anuncios".
- Backoffice: bandeja de informes registrales en revisión (documento a la izquierda, campos extraídos con su confianza a la derecha, confirmar o corregir), bandeja de anuncios por publicar, y verificación manual de identidad mientras no haya proveedor.
- El portal admin queda para F2; la edición del protocolo de fotos se hace en el backoffice con rol `ADMIN`.

## D7. Nivel de confianza en F1

`trust_level` puede ser `NONE` (sin ficha registral) o `REGISTRY_VERIFIED`. Los niveles de inspección llegan en F2. El anuncio solo se publica con `REGISTRY_VERIFIED` y con las fotos obligatorias completas.

## D8. Riesgos

- Formato del informe de rnpdigital: puede variar. Mitigación: esquema por versión de formato y revisión humana por defecto.
- Costo por informe si lo compra un operador: se traslada al vendedor como tarifa configurable.
- Línea base de V1 sobre una base creada con `update`: probar con una copia de la base antes de desplegar (puerta del dueño).
- Anuncios anteriores a F1: V6 copia `vehicle_spec_id` en `vehicle_id`, que no apunta a ninguna fila de `vehicle.vehicle`. Los que ya estaban en `PUBLISHED` siguen públicos con `trust_level` `NONE`, contra la regla de D7 que solo rige para publicaciones nuevas. Si la base real tiene anuncios publicados, el dueño decide en la tarea 0.4 si se pasan a `EXPIRED`.

## D9. Contrato de API (JSON; errores como `ErrorResponse` con `code` estable en MAYÚSCULAS, por ejemplo `VEHICLE_PLATE_INVALID`)

Público (`@PermitAll`, solo anuncios `PUBLISHED`):
- `GET /listings?brand&model&yearFrom&yearTo&minPrice&maxPrice&maxMileage&province&fuel&transmission&trustLevel&sort=RELEVANCE|PRICE_ASC|PRICE_DESC|YEAR_DESC&page&size` devuelve `{items: ListingSummary[], page, size, total}`.
- `GET /listings/{id}` devuelve `ListingDetail` con `vehicle.attributes[]` (`key`, `value`, `source`, `verified`) y `photos[]` (`slot`, `publicUrl`).
- `GET /media/photo-protocol?country=CR` devuelve `PhotoSlot[]` (`code`, `required`, `order`, `instructionKey`).

Usuario autenticado:
- `GET /me/profile`, `PUT /me/profile` (`fullName`, `idType`, `idNumber`, `phone`, `countryCode`); la respuesta nunca devuelve `idNumber` completo, solo los 4 últimos dígitos.
- `GET /me/consents`, `POST /me/consents` (`type`, `textVersion`, `granted`).
- `POST /me/identity-verifications` devuelve `{id, challengeCode, expiresAt}`; `PUT /me/identity-verifications/{id}` (`documentFrontMediaId`, `documentBackMediaId`, `selfieMediaId`) la envía; `GET /me/identity-verifications/latest`.
- `POST /media/uploads` (`kind`, `contentType`, `sizeBytes`, `photoSlot` opcional) devuelve `{mediaId, uploadUrl, expiresAt}`; `POST /media/{id}/confirm` devuelve `Media`; `GET /media/{id}/read-url` solo para el dueño o `BACKOFFICE`.

Vendedor (`SELLER`):
- `POST /vehicles` (`countryCode`, `plate`) devuelve `Vehicle` (reutiliza si existe); `GET /vehicles/{id}`.
- `POST /vehicles/{id}/registry-documents` (`mediaId`): el vehículo pasa a `REGISTRY_IN_REVIEW` y la extracción corre en segundo plano.
- `POST /vehicles/{id}/registry-requests` (`acceptedFee`) crea la tarea "que Motoria lo obtenga".
- `POST /listings` (`vehicleId`, `price`, `mileage`, `province`, `transmission`, `declaredEquipment[]`, `maintenanceHistory`, `negotiable`, `title`, `description`); `PUT /listings/{id}`; `GET /me/listings`.
- `PUT /listings/{id}/photos/{slot}` (`mediaId`); `DELETE /listings/{id}/photos/{slot}`.
- `POST /listings/{id}/copy-suggestions` devuelve `{inferenceId, title, description}`.
- Se mantienen `PATCH /listings/{id}/submit-review` y `PATCH /listings/{id}/mark-sold`.

Backoffice (`BACKOFFICE`, `ADMIN`):
- `GET /backoffice/registry-reviews?status`; `GET /backoffice/registry-reviews/{vehicleId}` (páginas del documento como imágenes y campos con `confidence` y `page`); `PUT /backoffice/registry-reviews/{vehicleId}/attributes/{key}` (`value`); `POST /backoffice/registry-reviews/{vehicleId}/approve`; `POST /backoffice/registry-reviews/{vehicleId}/reject` (`reason`).
- `GET /backoffice/registry-requests?status`; `POST /backoffice/registry-requests/{id}/document` (`mediaId`).
- `GET /backoffice/identity-verifications?status`; `POST /backoffice/identity-verifications/{id}/approve`; `POST /backoffice/identity-verifications/{id}/reject` (`reason`).
- Se mantiene `PATCH /listings/{id}/publish`; `GET /backoffice/listings?status=PENDING_REVIEW`.
- `PUT /backoffice/photo-protocol/{country}/{code}` (`required`, `order`, `instructionKey`), solo `ADMIN`.

### D9.1 Precisiones del contrato (2026-11-21, tras el primer borrador del frontend)

1. `Vehicle` (respuesta de `POST /vehicles` y `GET /vehicles/{id}`) incluye `registryRequestFee` (`amount`, `currency`), leída de configuración. Además: `rejectionReason`, `transferBlockedReason`, `attributes[]` y `encumbrances[]`.
2. `GET /listings/{id}`: anónimos y `BUYER` solo ven `PUBLISHED`; el dueño `SELLER` ve su anuncio en cualquier estado, con `photos[]` (incluye las privadas por slot, con su `mediaId`); `BACKOFFICE` y `ADMIN` ven todos.
3. Toda lista devuelve una página `{items, page, size, total}` numerada desde 0: `/listings`, `/me/listings`, `/me/consents`, y todas las bandejas `/backoffice/*`.
4. `ListingSummary`: `id`, `status`, `title`, `price`, `currency`, `modelYear`, `mileage`, `brand`, `model`, `province`, `trustLevel`, `coverPhotoUrl`, `vehicleId`.
5. Catálogos (enumeraciones en inglés): `province` `SAN_JOSE`, `ALAJUELA`, `CARTAGO`, `HEREDIA`, `GUANACASTE`, `PUNTARENAS`, `LIMON`; `fuel` `GASOLINE`, `DIESEL`, `HYBRID`, `PLUG_IN_HYBRID`, `ELECTRIC`, `LPG`, `OTHER`; `transmission` `MANUAL`, `AUTOMATIC`, `CVT`, `DUAL_CLUTCH`; `idType` `NATIONAL_ID`, `RESIDENT_ID`, `PASSPORT`. Los estados de bandejas son los del backend; el frontend se alinea a ellos.
6. `POST /listings` admite `title` y `description` vacíos mientras el anuncio está en `DRAFT`; se exigen al pasar a `PENDING_REVIEW`. Así `copy-suggestions` funciona antes de que el vendedor escriba el título.
7. El protocolo de fotos se siembra con `instructionKey` = `photoProtocol.slots.<CODE>.instruction`.
8. `PUT /me/profile`: `idNumber` es opcional; si no viene, se conserva el guardado.
9. `GET /me/consents` incluye por tipo la `currentTextVersion` vigente (configuración `motoria.consent.text-version`); el frontend la usa en lugar de una constante.
10. Roles reales por grupo: los endpoints de vendedor aceptan `SELLER`, `DEALER` y `ADMIN`; `POST /vehicles` y `GET /vehicles/{id}` aceptan además `BACKOFFICE`. El portal cliente trata `SELLER`, `DEALER` y `ADMIN` como vendedores.
11. Campos que el backend agrega al contrato: `Vehicle` incluye `reviewReason`, `registryRequestOpen` y `ownerMatch` (`MATCH`, `MISMATCH`, `UNKNOWN`); `POST /listings` y `PUT /listings/{id}` aceptan `currency` opcional (`CRC` por defecto o `USD`); `POST /listings/{id}/copy-suggestions` acepta `?language=es|en` (por defecto `es`); `POST /vehicles/{id}/registry-requests` responde 201 con `RegistryRequest`; `POST /vehicles` responde 200; `ErrorResponse` puede traer `details`.

## D10. Decisiones de implementación del backend (2026-11-21)

- **Puertos entre módulos.** Los módulos no se importan entre sí. Las pocas interacciones síncronas imprescindibles son interfaces del núcleo compartido `ai.motoria.common.port`, implementadas por el módulo dueño de los datos: `UserIdentityPort` (`user`: nivel de identidad y hash de la cédula, nunca la cédula), `VehicleRegistryPort` (`vehicle`: ficha sin datos del propietario y comparación de titularidad; la usa `listing` para prellenar y validar), `MediaPort` (`media`: metadatos, protocolo de fotos, lectura de contenido y URL de lectura, siempre registradas en `media.media_access_log`), `RegistryDocumentReaderPort` y `ListingCopyPort` (`ai`). Lo asíncrono va por eventos y outbox.
- **Esquema `usr`.** El módulo `user` usa el esquema `usr` (`user` es palabra reservada en PostgreSQL), como fija `BACKEND_ARCHITECTURE.md`.
- **V1 y nombres de columnas.** `Listing` no tenía nombres explícitos y Hibernate usaba la estrategia física por defecto (identidad), así que la base creada con `update` tiene `sellerid`, `vehiclespecid`, `modelyear`, `createdat` y `updatedat`. V1 los reproduce tal cual; V6 los renombra a snake_case solo si existen (funciona después de V1 y sobre una base tomada como línea base). Todas las entidades llevan ahora `@Column(name = ...)` explícito.
- **Auditoría.** `AuditableEntity` llena `created_*` y `updated_*` con el `sub` de Keycloak (o `system` fuera de una petición: consumidores y tareas).
- **Cédula.** Cifrado AES-256-GCM (llave `MOTORIA_USER_CRYPTO_KEY`) y hash HMAC-SHA256 con llave propia (`MOTORIA_IDENTITY_HASH_KEY`) compartido por `user` y `vehicle`: un SHA-256 simple de una cédula de 9 dígitos se revierte por fuerza bruta. La cédula del propietario leída por la IA se convierte en hash dentro de `ai` y nunca se guarda en claro (tampoco en `ai_inference.output_json`).
- **Verificación manual.** Código de 6 caracteres sin ambiguos, vigencia configurable; el cupo diario cuenta envíos del día (zona `America/Costa_Rica`) y responde 429 `IDENTITY_MANUAL_QUOTA_EXHAUSTED`. Un analista no puede aprobar su propia verificación.
- **Almacenamiento.** AWS SDK v2 (`s3` 2.28.29 con `url-connection-client`, sin Netty ni Apache) con credenciales de la cadena por defecto de AWS. Bucket privado para originales y documentos; bucket público solo para copias de fotos.
- **EXIF.** No se agrega librería: la copia pública se decodifica, se rota según la orientación EXIF (lector propio de la cabecera) y se recodifica a JPEG con el JDK, lo que descarta EXIF, XMP, IPTC y GPS. Por eso las fotos de anuncio aceptan JPEG y PNG; HEIC solo se acepta para documentos de identidad (nunca públicos), porque el JDK no lo decodifica.
- **PDF.** Apache PDFBox 3.0.3: texto por página para la IA (si el PDF es escaneado se envían las páginas como imagen) y render de páginas a PNG para la bandeja del analista.
- **IA.** `motoria.ai.provider=stub` (sin llamadas externas; la extracción devuelve vacío y todo va al analista) u `openai-compatible` (adaptador HTTP genérico a `{base-url}/chat/completions`). Temperatura 0 configurable (`motoria.ai.send-temperature`) porque hay proveedores que rechazan el parámetro. La aprobación automática queda apagada hasta medir la precisión (tarea 4.5).
- **Revisión registral.** Motivos: `MANUAL_REVIEW_DEFAULT`, `LOW_CONFIDENCE`, `DOCUMENT_TOO_OLD`, `PLATE_MISMATCH`, `MISSING_REQUIRED`, `EXTRACTION_FAILED`. Con placa distinta o extracción fallida no se aplica ningún dato. Para corregir el propietario el analista envía la cédula en `PUT .../attributes/OWNER_ID_HASH` y se guarda su hash. Un analista no revisa un documento que él mismo envió.
- **Titularidad.** Sin flujo de poder en F1: si la cédula verificada del vendedor no coincide con el propietario registral, `submit-review` responde `LISTING_SELLER_NOT_OWNER`.
- **Publicación.** `submit-review` y `publish` aplican la misma política; el error lleva el primer código y en `details` todos los códigos y los slots faltantes.
- **Catálogo de anuncios.** `brand`, `model`, `fuel` y `model_year` se copian del vehículo a `listing.listing` para buscar sin uniones entre esquemas; se agrega `currency` (CRC por defecto, admite USD).
- **Alta concurrente.** Crear un vehículo por placa o el perfil en el primer acceso se hace en una transacción propia (`VehicleCreator`, `UserProfileCreator`, `REQUIRES_NEW` con `persistAndFlush`); si otra petición ganó, la violación única (`common/persistence/UniqueViolations`, SQLState 23505) se ignora y se relee la fila. En el resto (foto por slot, protocolo de fotos, anuncio activo, solicitud registral abierta) el servicio hace `flush` dentro del método y traduce la violación a `InvalidStateException` (409) con código estable. El render de páginas y las URL de lectura de `MediaPort` corren en `REQUIRES_NEW` para no dejar en solo-rollback la transacción del analista. V7 agrega índices únicos parciales: un anuncio activo (`DRAFT`, `PENDING_REVIEW`, `PUBLISHED`) por vendedor y vehículo, y una solicitud registral `PENDING` por vehículo. Antes de crear el primero, V7 **cambia datos**: pasa a `EXPIRED` los anuncios activos duplicados por vendedor y vehículo y conserva el más reciente (antes de F1 nada lo impedía); el comentario de V7 trae la consulta para verlos antes de desplegar. F1 no tiene forma de reactivar un anuncio expirado salvo SQL manual.
