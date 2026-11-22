# MVP: de la placa al anuncio verificado (F1)

Implementa la fase **F1** del roadmap (design D6 de `define-plataforma-motoria`, aprobado por el dueño el 2026-11-21).

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

### Requirements de la base cubiertos por F1

| Capacidad | Implementa en F1 | Difiere (fase) |
|---|---|---|
| `vehiculo-registro` | Ficha por placa (vía documento), fuentes intercambiables y sin evasión, verificado frente a declarado en la ficha, gravámenes bloqueantes, titularidad sin poder | `vehicle.registry.requested` se publica al recibir el documento y no al escribir la placa (D1 paso 5); titularidad con poder (F5); vigencia de los datos antes del traspaso (F5); diferencias declarado frente a registro: el vendedor no declara datos registrales en F1, la tabla queda lista (F2, con VIN y color de las fotos) |
| `publicacion-anuncios` | Captura guiada, asistente de título y descripción, protocolo de fotos (presencia y calidad básica), ciclo de vida sin `RESERVED`, nivel de confianza `NONE` o `REGISTRY_VERIFIED`, búsqueda y filtros | Sugerencia de precio y detección de omisiones del asistente (F3); foto de otro vehículo (F2); `RESERVED` (F5); filtro "Certificado Motoria" y orden por precio justo (F2 y F3) |
| `identidad-cumplimiento` | Niveles de identidad, consentimientos granulares, auditoría de accesos y trazabilidad de la IA, seguridad de la IA conversacional | Derechos del titular (exportar y eliminar) y prevención de fraude y LC/FT (fase por definir; depende de las tareas 1.2 a 1.4 de la base) |

## Impact

- Specs: implementa los requirements de la tabla anterior y agrega `plataforma-base` y la lectura del informe registral (deltas de este change).
- Código: módulos `user`, `vehicle`, `media`, `listing`, `ai`, `common`; nuevos `frontend/apps/portal-cliente`, `frontend/apps/backoffice` y paquetes `frontend/packages/api`, `ui` e `i18n`.
- Base de datos: migraciones Flyway reservadas **V1 a V19**. Riesgo: la base actual se generó con `update`; V1 la toma como línea base (design D2).
- Eventos nuevos: `vehicle.registry.requested`, `vehicle.registry.completed`, `vehicle.registry.review.required`.
- Puertas del dueño: proveedor de identidad (base 1.1), inscripción ante PRODHAB (base 1.2), proveedor y llave del modelo de IA (0.1), bucket S3 (0.2), 20 informes para medir precisión (0.3), copia de la base (0.4), tarifa del informe (0.5), realm de Keycloak (0.6) y rutas de APISIX (0.7).
