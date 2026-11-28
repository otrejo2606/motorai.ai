# Design

## D1. Contrato de Credid (tomado del gateway MNK Acsel)

- Fuente: el respaldo `MNKAcselGateway`. Se usaron `CredidRestClient`, `CredidPersonaResponse` y la configuración `credid-client`, recuperados del índice de git del respaldo. Ese código es el que MNK usa en producción.
- Llamada: `GET {url}?cedula={número}` con el token tal cual en `Authorization`, sin `Bearer`. Por decisión del dueño, hasta que Motoria tenga su propio contrato (puerta 0.1) solo se usa el ambiente de pruebas de Credid. Su URL, `https://ws.credid.net/wstest/api/Reporte`, es el valor por defecto de `motoria.user.person-registry.credid.url`. La URL de producción se configurará con `MOTORIA_CREDID_URL` cuando exista el contrato.
- Respuesta: objeto con `Tipo`, `ConsultaId` y una de `FiliacionFisica`, `FiliacionJuridica` o `FiliacionExtranjero`, más `Localizacion`, `ActividadEconomica`, `Matrimonios`, `PEP` y `APNFD`.
- Se toma solo esto:

  | Persona | Nombre | Vencimiento | Nacionalidad |
  |---|---|---|---|
  | Física | `Nombre Apellido1 Apellido2` | `VencimientoCedula` | `Nacionalidad` o `Pais` |
  | Extranjera | igual que la física | `FechaVencimientoDocumento` | `Nacionalidad` o `Pais` |
  | Jurídica | `Nombre` | sin vencimiento | `Pais` |

  Además se guarda `ConsultaId` como referencia del proveedor.
- Sin confirmar con Credid:
  - qué responde cuando la cédula no existe. Se trata como no encontrada una respuesta 404, vacía o sin ninguna filiación;
  - el formato de las fechas. Se aceptan ISO, con o sin hora, y `dd/MM/yyyy` estricto; lo demás queda como desconocido.
- Errores:
  - 401 o 403: `PERSON_REGISTRY_NOT_CONFIGURED`;
  - otro estado fuera de 2xx, red, tiempo agotado o respuesta ilegible: `PERSON_REGISTRY_UNAVAILABLE`.

  Ambos responden 503 con código estable.
- La cédula nunca se escribe en logs. El adaptador usa `java.net.http.HttpClient`, igual que `HttpLlmAdapter`: no se agrega la extensión REST Client.

## D2. Selección del adaptador

`PersonRegistryPortProducer` lee `motoria.user.person-registry.provider`, que acepta `credid` o `stub`. Cualquier otro valor cae en `stub`, para que un error de escritura nunca dispare consultas pagadas. El stub responde así:

| El número termina en | Respuesta |
|---|---|
| `0000` | no encontrada |
| `9999` | documento vencido |
| cualquier otro | nombre `motoria.user.person-registry.stub-full-name` (por defecto «Persona de Prueba Motoria») |

## D3. Caché, cupo y bitácora

- `usr.person_registry_check`: una fila por consulta pagada, indexada por `id_number_hash`, el mismo HMAC con llave que `user_profile`. La cédula no se guarda.
- Se reutiliza la consulta más reciente del mismo hash y proveedor mientras no supere `cache-ttl` (P90D; decisión del dueño del 2026-11-26, la misma vigencia que los datos del convenio de vehículos). `refresh=true` en el backoffice obliga a pagar una nueva.
- Cupo diario de consultas pagadas: `daily-quota` (200), contado por día de Costa Rica y solo con las filas del proveedor activo (las del stub no consumen el cupo de Credid). Al agotarse, la consulta del backoffice responde 429 `PERSON_REGISTRY_QUOTA_EXHAUSTED` y el cotejo de una verificación queda en `UNAVAILABLE`.
- `usr.person_registry_access`: una fila por uso, pagado o reutilizado. Guarda el propósito (`IDENTITY_VERIFICATION` o `BACKOFFICE_LOOKUP`), el motivo (obligatorio en el backoffice, de 10 a 300 caracteres), la verificación cuando aplica y el usuario en `created_by`.
- Las fallas no se guardan: un nuevo intento vuelve a consultar.

## D4. Cotejo en la verificación de identidad

- En `submit`, después de pasar el caso a `SUBMITTED`, se consulta el registro con el tipo y la cédula del perfil, que se descifran solo para la llamada.
- El estado se decide en este orden de prioridad:
  1. `NOT_APPLICABLE`: pasaporte, o perfil sin cédula.
  2. `UNAVAILABLE`: falla o cupo agotado.
  3. `NOT_FOUND`.
  4. `DOCUMENT_EXPIRED`: vencimiento anterior a hoy en Costa Rica.
  5. `NAME_MISMATCH`.
  6. `MATCH`.
- Comparación de nombres (`RegistryNameMatcher`): sin mayúsculas, tildes, signos ni orden. Hay coincidencia si el perfil tiene al menos dos palabras y todas están en el nombre del registro. El registro suele traer los dos apellidos y a veces un segundo nombre que el usuario omite.
- El resultado se guarda en `identity_verification.registry_status` y `registry_check_id`. No cambia el flujo: el analista aprueba o rechaza como antes, porque el registro no tiene prueba de vida.
- La llamada corre dentro de la transacción de `submit`, con un tiempo máximo de `timeout` (PT15S). Una falla del registro no revierte el envío: se captura `DependencyUnavailableException` (registro caído o mal configurado, o falta la llave para descifrar la cédula) o `QuotaExceededException` y queda `UNAVAILABLE`.
- Costo aceptado de esa decisión:
  - si la transacción se revierte después de la llamada (por ejemplo, una falla de la base al confirmar), la consulta ya se pagó, pero no queda en caché ni cuenta para el cupo;
  - la conexión a la base queda abierta mientras se espera a Credid, hasta 15 s.

  Si el volumen lo exige, la consulta se moverá a un paso posterior al envío (outbox).
- `POST /backoffice/identity-verifications/{id}/registry-check` (BACKOFFICE y ADMIN, auditado) repite el cotejo de un caso `SUBMITTED`. Reusa la consulta vigente, así que no paga dos veces; el analista no puede cotejar su propio caso.

## D5. Consulta del backoffice

- `POST /backoffice/person-registry/lookups`, solo para BACKOFFICE y ADMIN y auditado.
- Se usa POST para que el número viaje en el cuerpo y nunca quede en URLs ni en logs de acceso.
- Acepta `NATIONAL_ID` (física o jurídica) y `RESIDENT_ID`. El pasaporte responde 422 `PERSON_REGISTRY_ID_TYPE_UNSUPPORTED`.
- El número se normaliza como en el perfil (solo letras y dígitos) y debe tener entre 5 y 20 caracteres.
- La respuesta es mínima: encontrado, proveedor, tipo de persona, nombre, vencimiento, si está vencido, nacionalidad, fecha de la consulta y si se reutilizó.

## D6. Regla de la placa para la consulta de vehículos (implementada en `add-consulta-credid-vehiculos`)

El gateway MNK Acsel consulta vehículos por un procedimiento de Oracle Acsel. El contrato directo con Credid sale del gateway WSO2 (`QueryCredid`) y lo implementa `add-consulta-credid-vehiculos` (design D1 y D2), que usa esta regla en `CredidPlateClass`. Falta la URL del servicio de vehículos (puerta 0.1 de vehículos). La regla de la placa sale de `seqBackGroundCredid` del gateway WSO2:

| La placa empieza con | Clase | Número enviado |
|---|---|---|
| `CL` | CL | el resto |
| `C` | C | el resto |
| `D` seguida de un dígito | D | el resto |
| cualquier otra cosa | A | la placa completa |

Los campos que se toman de la respuesta están en el design D1 de `add-consulta-credid-vehiculos`.

## D7. Migración

V8 en el rango reservado de F1 (V1 a V19; F1 usó hasta V7). F2 usa V20 a V39, así que no hay colisión.

Crea `usr.person_registry_check` y `usr.person_registry_access` con columnas de auditoría, índices y `CHECK`, y agrega `registry_status` y `registry_check_id` a `usr.identity_verification`.
