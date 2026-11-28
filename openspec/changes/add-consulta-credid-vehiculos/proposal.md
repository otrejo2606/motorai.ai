# Consulta del vehículo por placa en el registro (Credid)

## Why

En F1 el vendedor compra el informe registral en rnpdigital, lo sube en PDF y espera a que la IA y un analista lo lean. Es lento, le cuesta al vendedor y depende de un documento que puede venir viejo o ilegible. Credid, el mismo proveedor que ya se usa para personas (`add-consulta-credid-personas`), responde por placa la ficha del vehículo, el dueño registral y si tiene prenda. El gateway WSO2 de MNK lo consume con el mismo token, así que el contrato se conoce.

Motoria pasa a usar Credid como **primera opción** y deja el PDF como **contingencia** cuando Credid no encuentra la placa o no responde.

## What Changes

- **Orden nuevo del vendedor:**
  1. placa;
  2. datos declarados (precio, kilometraje, provincia, etc.), que crean el borrador del anuncio;
  3. fotos del protocolo;
  4. consulta al Registro Nacional por convenio (Credid), automática al terminar las fotos;
  5. solo si Credid no pudo responder: subir el informe PDF (contingencia), como en F1;
  6. ficha verificada;
  7. envío a revisión.
- **Puerto `VehicleRegistryLookupPort`** en `vehicle`, con dos adaptadores que se eligen por configuración: `credid` (HTTP, URL propia de vehículos y el mismo token de personas) y `stub` (nunca sale a la red).
- **`POST /vehicles/{vehicleId}/registry-lookup`** (SELLER, DEALER, ADMIN; auditado). Nunca responde 5xx por una falla de Credid: devuelve `registryLookupStatus` (`FOUND`, `REUSED`, `NOT_FOUND`, `UNAVAILABLE`, `NOT_CONFIGURED`, `QUOTA_EXHAUSTED`) y, si no hay datos, el portal pide el PDF.
- **Con datos completos el vehículo queda verificado sin analista** (configurable con `agreement-auto-approve`): atributos con fuente `REGISTRY_AGREEMENT`, prenda como gravamen `PLEDGE` y el mismo evento `vehicle.registry.completed` de F1. Si falta un dato obligatorio o la aprobación automática está apagada, el caso va a la bandeja del analista, que ya acepta casos sin documento.
- **Validación y anomalías.** Se compara el dueño registral (hash de la cédula) con la cédula verificada del vendedor y se detecta la prenda.
  - Si algo no calza, el vendedor lo ve en pantalla y recibe una comunicación (evento `vehicle.registry.anomaly.detected`, que consume el módulo `notification`).
  - Un dueño distinto no aprueba la publicación: la regla de titularidad de F1 sigue impidiendo enviar el anuncio a revisión.
- **Vigencia y costo:**
  - los datos del convenio valen 90 días (`agreement-max-age`) y se reutilizan sin pagar otra consulta; el PDF sigue valiendo 30 días;
  - hay un cupo diario de consultas por proveedor;
  - cada llamada queda en la bitácora `vehicle.registry_lookup`.
- **Gancho para el traspaso (F5):** `refreshForTransfer` obliga a consultar de nuevo antes de firmar y después de la compraventa. Credid solo informa prendas: embargos, anotaciones y colisiones siguen exigiendo un informe fresco en el traspaso.
- **Personas:** la caché del registro de personas pasa de 30 a 90 días (`add-consulta-credid-personas`).
- **Migración V9** (rango de F1).

## Impact

- Specs: delta de `vehiculo-registro`.
- Código:
  - `vehicle` (dominio, integración, servicio, REST, mapper, eventos);
  - `notification` (consumidor y servicio);
  - `listing`: `ListingPublicationGuard` vuelve a copiar la ficha registral al enviar, porque el borrador puede crearse antes de la verificación;
  - `common/event/EventType`;
  - `V9__vehicle_registry_lookup.sql`;
  - `application.properties`.
- Portal: asistente de venta reordenado y paso nuevo `RegistryLookupStep`. Backoffice: revisión registral sin documento. Además `@motoria/api` y `@motoria/i18n` (es y en).
- Variables de entorno nuevas: `MOTORIA_VEHICLE_REGISTRY_PROVIDER` y `MOTORIA_CREDID_VEHICLE_URL`. El token es el de personas, `MOTORIA_CREDID_TOKEN`.
- RabbitMQ: la cola existente `notification.vehicle-registry-events` suma la clave `vehicle.registry.anomaly.detected`.
- Solo el ambiente de pruebas de Credid, hasta que Motoria tenga contrato propio (puerta 0.1 de personas).
