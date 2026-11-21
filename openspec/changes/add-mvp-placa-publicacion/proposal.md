# MVP: de la placa al anuncio verificado (F1)

Implementa la fase **F1** del roadmap (design D6 de `define-plataforma-motoria`). Depende de que se apruebe ese change (tarea 0.5).

## Why

Para competir con Crautos desde el primer día, Motoria necesita su diferencia mínima visible: un anuncio cuya ficha viene del registro oficial y no de lo que escribe el vendedor, con identidad verificada y fotos completas. Hoy el backend solo tiene `listing` con un `vehicleSpecId` sin entidad detrás, sin migraciones (Hibernate `generation=update`), sin identidad ni consentimientos, sin carga de archivos y sin frontend. La investigación del 2026-11-21 no halló API comercial del Registro Nacional (por verificar, base D7.1 y D8.1); por eso F1 lee el informe registral de rnpdigital.com con IA y deja la consulta por convenio para F7.

## What Changes

- **Plataforma base:** migraciones versionadas con Flyway (se retira `generation=update`), esquema por módulo, i18n español e inglés, y los portales cliente y backoffice en React con TypeScript (aún no existen).
- **`user`:** perfil, niveles de identidad, consentimientos granulares versionados (Ley 8968) y un puerto de verificación de identidad con adaptador manual (documento y selfie con código de un solo uso revisados por un analista) hasta elegir proveedor (tarea 1.1 de la base).
- **`vehicle`:** entidad `Vehicle` por placa y país, con cada dato ligado a su fuente; `RegistroNacionalPort` con `RegistroDocumentoAdapter`: el vendedor o un operador sube el informe registral en PDF, la IA lo lee y un analista confirma los casos dudosos; gravámenes bloqueantes y titularidad del vendedor.
- **`media`:** carga segura a S3 (tipo, tamaño, sha256, metadatos), protocolo de fotos configurable y validación básica de calidad.
- **`listing`:** el anuncio se liga al `Vehicle`, se prellena con lo verificado, distingue verificado y declarado, muestra el nivel de confianza y gana filtros de búsqueda.
- **`ai`:** registro `ai_inference` y un puerto de modelo configurable; dos usos: lectura del informe registral y asistente de título y descripción.

Fuera de F1: peritaje de daños, contenido de fotos por IA y huella perceptual (F2), precio justo (F3), seguros, traspaso, crédito y el estado `RESERVED` (F4 a F6).

## Impact

- Specs: implementa requirements de `vehiculo-registro`, `publicacion-anuncios` e `identidad-cumplimiento` de la base, y agrega `plataforma-base` y la lectura del informe registral (deltas de este change).
- Código: módulos `user`, `vehicle`, `media`, `listing`, `ai`, `common`; nuevos `frontend/portal-cliente` y `frontend/backoffice`.
- Base de datos: migraciones Flyway reservadas **V1 a V19**. Riesgo: la base actual se generó con `update`; V1 la toma como línea base (design D2).
- Eventos nuevos: `vehicle.registry.requested`, `vehicle.registry.completed`, `vehicle.registry.review.required`.
- Puertas del dueño: proveedor de identidad (base 1.1), inscripción ante PRODHAB (base 1.2), proveedor y llave del modelo de IA, bucket S3.
