# Design: MVP placa y publicación (F1)

## D1. Flujo del vendedor

1. Registro e inicio de sesión con WSO2 IS (rol `SELLER`). Para publicar se exige identidad nivel 2 (documento y prueba de vida). Mientras no haya proveedor (tarea 1.1 de la base), la vía manual es: el vendedor sube foto del documento por ambos lados y una selfie sosteniendo el documento junto a un código de un solo uso que muestra la pantalla; un analista `BACKOFFICE` compara rostro, documento y código y aprueba o rechaza, con registro del analista y la evidencia. Esta vía tiene un cupo diario configurable; si se agota, la publicación espera.
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

- `user.user_profile`: id (sujeto de WSO2), nombre, cédula y tipo (cifrados en reposo), teléfono, correo, país, `identity_level` (`LEVEL_1`, `LEVEL_2`, `LEVEL_3`), proveedor y referencia de la verificación.
- `user.consent`: usuario, tipo (`DATA_PROCESSING`, `SHARE_INSURANCE_BROKER`, `SHARE_BANK`, `PHOTO_LOCATION`, `MODEL_TRAINING`), versión del texto, otorgado o revocado, fecha, IP.
- `vehicle.vehicle`: id, `country_code`, `plate` (normalizada), `status` (`REGISTRY_PENDING`, `REGISTRY_IN_REVIEW`, `REGISTRY_VERIFIED`, `REGISTRY_REJECTED`), `transfer_blocked` y motivo, `registry_checked_at`. Único por (`country_code`, `plate`).
- `vehicle.vehicle_attribute`: vehículo, clave (`BRAND`, `MODEL`, `STYLE`, `YEAR`, `COLOR`, `VIN`, `ENGINE_NUMBER`, `DISPLACEMENT_CC`, `FUEL`, `DRIVETRAIN`, `DOORS`, `CAPACITY`, `OWNER_ID_HASH`, `OWNER_NAME`), valor, `source` (`REGISTRY_DOCUMENT`, `REGISTRY_AGREEMENT`, `SELLER_DECLARED`, `ANALYST`), evidencia (id de media y página), confianza, fecha. El propietario se guarda como hash de la cédula más el nombre, nunca la cédula en claro.
- `vehicle.registry_encumbrance`: vehículo, tipo (`PLEDGE`, `SEIZURE`, `ANNOTATION`, `COLLISION`, `OTHER`), descripción, si bloquea el traspaso, evidencia.
- `vehicle.registry_discrepancy`: vehículo, clave, valor registral, valor declarado, estado.
- `media.media_object`: id, dueño, tipo (`REGISTRY_DOCUMENT`, `LISTING_PHOTO`, `IDENTITY_DOCUMENT`), `photo_slot` del protocolo, clave S3, `content_type`, tamaño, `sha256`, ancho y alto, metadatos de captura, visibilidad (`PRIVATE`, `PUBLIC`).
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
