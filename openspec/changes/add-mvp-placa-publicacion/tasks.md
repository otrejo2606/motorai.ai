# Tasks

Fase F1. Marcas: (A) agente, (B) puerta del dueño, (C) calendario. Una marca [x] significa "escrito", nunca "compilado ni probado". Migraciones reservadas: **V1 a V19**. Todo depende de la aprobación de `define-plataforma-motoria` (tarea 0.5 de la base).

## 0. Puertas

- [ ] 0.1 (B) Proveedor y modelo de IA, y su llave en el gestor de secretos.
- [ ] 0.2 (B) Bucket S3 y credenciales por entorno.
- [ ] 0.3 (B) Entregar 20 informes registrales reales de rnpdigital.com enmascarados para el conjunto de prueba.
- [ ] 0.4 (B) Copia de la base actual para probar la línea base de V1.
- [ ] 0.5 (B) Tarifa del informe obtenido por Motoria.

## 1. Plataforma base (`common`, configuración)

- [ ] 1.1 (A) Agregar `quarkus-flyway`; `generation=none`; `migrate-at-start` y `baseline-on-migrate`.
- [ ] 1.2 (A) V1 línea base de `listing.listing` y `outbox_event` (esquema por defecto) con `IF NOT EXISTS`, idéntica al mapeo actual (depende de 1.1).
- [ ] 1.3 (A) Llenado de columnas de auditoría desde el sujeto autenticado.
- [ ] 1.4 (A) Códigos de error estables en `ErrorResponse` para el frontend.
- [ ] 1.5 (A) Eventos `vehicle.registry.requested`, `vehicle.registry.completed` y `vehicle.registry.review.required` en `EventType`, con cola, reintento y DLQ en `application.properties`.

## 2. `user`

- [ ] 2.1 (A) V2: `user_profile` y `consent`.
- [ ] 2.2 (A) Perfil con cédula cifrada en reposo y hash para comparar con el propietario registral.
- [ ] 2.3 (A) Consentimientos versionados y revocables; textos en i18n.
- [ ] 2.4 (A) `IdentityVerificationPort` con adaptador manual (documento, selfie con código de un solo uso, aprobación del analista y cupo diario; design D1) y niveles de identidad.

## 3. `media`

- [ ] 3.1 (A) V3: `media_object` y `photo_protocol_slot` con la semilla del protocolo.
- [ ] 3.2 (A) URL prefirmada de subida, confirmación con verificación de firma de contenido, tamaño y sha256 (depende de 0.2).
- [ ] 3.3 (A) Copia pública sin EXIF; lectura privada con URL de corta duración y auditoría.
- [ ] 3.4 (A) Validación de calidad básica de fotos (resolución mínima) y protocolo completo.

## 4. `ai`

- [ ] 4.1 (A) V5: `ai_inference`.
- [ ] 4.2 (A) `LlmPort` y adaptador del proveedor elegido; configuración sin llaves en el repo (depende de 0.1).
- [ ] 4.3 (A) Caso `REGISTRY_EXTRACTION`: esquema de salida, cita por página, validación y descarte, defensa contra instrucciones en el documento.
- [ ] 4.4 (A) Caso `LISTING_COPY`: título y descripción solo con datos de la ficha y del vendedor.
- [ ] 4.5 (A) Medición de precisión por campo con el conjunto de prueba (depende de 0.3).

## 5. `vehicle`

- [ ] 5.1 (A) V4: `vehicle`, `vehicle_attribute`, `registry_encumbrance`, `registry_discrepancy` (depende de 3.1).
- [ ] 5.2 (A) Normalización y validación de placa por país (formatos en configuración).
- [ ] 5.3 (A) `RegistroNacionalPort` y `RegistroDocumentoAdapter`; flujo de estados y eventos.
- [ ] 5.4 (A) Reglas de bloqueo por gravámenes y comparación de titularidad.
- [ ] 5.5 (A) Solicitud "que Motoria lo obtenga" con tarea en el backoffice (depende de 0.5).

## 6. `listing`

- [ ] 6.1 (A) V6: `vehicle_id`, `province`, `transmission`, `trust_level` y datos declarados; migrar `vehicle_spec_id`.
- [ ] 6.2 (A) Creación prellenada desde el `Vehicle` y registro de discrepancias declarado frente a registro.
- [ ] 6.3 (A) Publicación solo con `REGISTRY_VERIFIED`, identidad nivel 2 y fotos obligatorias.
- [ ] 6.4 (A) Filtros de búsqueda nuevos y orden.
- [ ] 6.5 (A) Ajustar `ListingResource`, mapper, DTO y pruebas existentes al nuevo modelo.

## 7. Frontend

- [ ] 7.1 (A) Monorepo `frontend/` (Vite, React, TypeScript, React Hook Form, TanStack Query, react-i18next) con paquete `ui`.
- [ ] 7.2 (A) Portal cliente: búsqueda, ficha del anuncio con verificado y declarado, flujo vender y mis anuncios.
- [ ] 7.3 (A) Backoffice: bandeja de informes registrales, bandeja de anuncios, verificación manual de identidad, edición del protocolo de fotos.
- [ ] 7.4 (A) Archivos de traducción español e inglés con las mismas claves.

## 8. Pruebas (las ejecuta el CI)

- [ ] 8.1 (A) Unitarias de dominio: estados de `Vehicle`, bloqueos, titularidad, consentimientos.
- [ ] 8.2 (A) De servicio con el puerto de IA simulado: cita obligatoria, placa distinta, umbral.
- [ ] 8.3 (A) De REST: roles y propiedad en cada endpoint nuevo.
- [ ] 8.4 (A) De carga: firma de contenido falsa y tamaño excedido.
