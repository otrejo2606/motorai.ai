# Consulta de personas por documento en el registro (Credid)

## Why

La verificación de identidad de F1 depende solo del analista, que compara la selfie con el documento sin saber si la cédula existe, si está vencida o si el nombre del perfil es el del titular. MNK Seguros ya consulta a Credid en producción para esto: su gateway Acsel llama `GET .../api/Reporte?cedula=` con un token. Ese gateway nunca se migró y el código solo quedó en un respaldo, pero conserva el contrato de la respuesta. Motoria lo reusa para dar al analista un cotejo con el registro y para que el backoffice consulte a quién pertenece un documento.

La consulta de vehículos por placa de ese gateway no se reusa: no llama a Credid, sino a un procedimiento de Oracle Acsel que traduce marca y modelo a los catálogos de Acsel. La consulta de vehículos directa a Credid, con el contrato del gateway WSO2 de MNK, es el cambio `add-consulta-credid-vehiculos`.

## What Changes

- **Puerto `PersonRegistryPort`** en `user`, con dos adaptadores que se eligen por configuración: `credid` (HTTP, URL y token por entorno) y `stub` (desarrollo y pruebas; nunca sale a la red).
- **Al enviar una verificación de identidad**, el sistema consulta el registro con la cédula del perfil y guarda el resultado en el caso: `MATCH`, `NAME_MISMATCH`, `NOT_FOUND`, `DOCUMENT_EXPIRED`, `UNAVAILABLE` o `NOT_APPLICABLE`. Es informativo: no aprueba ni rechaza nada, porque el registro no comprueba que la persona esté presente. Si el registro falla, la verificación se envía igual.
- **Backoffice:** la tarjeta de cada verificación muestra lo que dice el registro y permite volver a consultar. Se agrega la página «Consulta de personas»: tipo y número de documento más un motivo obligatorio.
- **Costo y privacidad:**
  - Cada consulta pagada se guarda con el hash de la cédula, nunca con el número en claro, y se reutiliza durante 90 días (decisión del dueño del 2026-11-26, igual que los vehículos).
  - Hay un cupo diario de consultas pagadas.
  - Cada uso, pagado o reutilizado, queda en una bitácora con usuario, propósito y motivo.
  - Solo se guarda nombre, tipo de persona, vencimiento del documento y nacionalidad: ni direcciones, ni teléfonos, ni ingresos.
- **Migración V8** (rango de F1).

## Impact

- Specs: delta de `identidad-cumplimiento`.
- Código: `user` (dominio, integración, servicio, REST, mapper), `V8__usr_person_registry.sql`, `application.properties`, backoffice (`RegistryCheckPanel`, `PersonRegistryPage`, ruta y menú), `@motoria/api` (tipos y hooks), `@motoria/i18n` (es y en).
- Variables de entorno nuevas: `MOTORIA_PERSON_REGISTRY_PROVIDER`, `MOTORIA_CREDID_URL`, `MOTORIA_CREDID_TOKEN`.
- No cambia la tarea 1.1 de la base (proveedor con prueba de vida): Credid no la cubre.
