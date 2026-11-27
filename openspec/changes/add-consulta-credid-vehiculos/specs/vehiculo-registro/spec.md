## ADDED Requirements

### Requirement: Consulta del vehículo por placa en el registro como primera opción
El sistema DEBE (SHALL) consultar el registro de vehículos por convenio (Credid) con la placa cuando el vendedor termina sus datos declarados y sus fotos, antes de pedir el informe registral en PDF. NO DEBE (MUST NOT) responder un error del servidor por una falla del registro: en ese caso DEBE (MUST) indicar `registryLookupStatus` y dejar el vehículo como estaba para que el vendedor suba el PDF (contingencia).

#### Scenario: Orden del asistente de venta
- **WHEN** un vendedor escribe la placa, guarda sus datos declarados y completa las fotos obligatorias
- **THEN** el portal llama `POST /vehicles/{vehicleId}/registry-lookup`, muestra «Consultando el Registro Nacional…» y solo después ofrece la ficha del vehículo o, si no hubo datos, el paso del informe PDF

#### Scenario: Sin anomalías, verificado sin PDF
- **WHEN** el registro devuelve la placa con todos los datos obligatorios, el dueño registral coincide con la cédula verificada del vendedor y no hay prenda
- **THEN** el vehículo queda `REGISTRY_VERIFIED` con `registryLookupStatus = FOUND`, sus atributos tienen fuente `REGISTRY_AGREEMENT`, no se pide el PDF y se publica `vehicle.registry.completed`

#### Scenario: Credid caído
- **WHEN** el registro no responde, se agota el tiempo o responde un error
- **THEN** el servidor responde 200 con `registryLookupStatus = UNAVAILABLE` y `errorCode = VEHICLE_REGISTRY_UNAVAILABLE`, el vehículo sigue en `REGISTRY_PENDING` y el portal pide el informe PDF

#### Scenario: Placa no encontrada
- **WHEN** el registro responde 404, sin `Dato` o con otra placa en `Dato.Bien`
- **THEN** la respuesta trae `registryLookupStatus = NOT_FOUND`, el vehículo sigue en `REGISTRY_PENDING` y el portal pide el informe PDF

#### Scenario: Registro sin configurar o cupo agotado
- **WHEN** la URL de vehículos de Credid no está configurada, Credid rechaza el token o ya se hicieron en el día tantas consultas respondidas como `daily-quota`
- **THEN** la respuesta trae `NOT_CONFIGURED` o `QUOTA_EXHAUSTED` y el portal pide el informe PDF

#### Scenario: Faltan datos obligatorios
- **WHEN** el registro devuelve la placa sin alguno de los atributos obligatorios (por ejemplo, sin la identificación del dueño)
- **THEN** el vehículo queda `REGISTRY_IN_REVIEW` con `reviewReason = MISSING_REQUIRED` y el analista lo revisa en la bandeja sin documento ni páginas

#### Scenario: Sin permiso
- **WHEN** un usuario con rol BUYER o BACKOFFICE llama la consulta
- **THEN** el servidor responde 403

### Requirement: Anomalías del registro comunicadas al vendedor
El sistema DEBE (SHALL) comparar el dueño registral (hash de la identificación) con la cédula verificada del vendedor y detectar la prenda, mostrar las anomalías traducidas en la respuesta y en el portal, y enviar una comunicación al vendedor. Una anomalía de titularidad NO DEBE (MUST NOT) permitir enviar ni publicar el anuncio.

#### Scenario: Dueño distinto, comunicación
- **WHEN** el registro informa un dueño cuya identificación no coincide con la cédula verificada del vendedor
- **THEN** la respuesta trae `anomalies = [OWNER_MISMATCH]`, se publica `vehicle.registry.anomaly.detected` con el vendedor y el código (sin placa, nombre ni cédula), el módulo de notificaciones prepara el aviso al vendedor y el envío del anuncio a revisión se rechaza con `LISTING_SELLER_NOT_OWNER`

#### Scenario: Prenda
- **WHEN** el registro informa `PoseeGarantias` verdadero o `TotalPrenda` mayor que cero
- **THEN** el vehículo tiene un gravamen `PLEDGE` que bloquea el traspaso, la respuesta trae `anomalies = [PLEDGE]` y se envía la comunicación al vendedor

#### Scenario: Vendedor sin cédula verificada
- **WHEN** el vendedor aún no tiene la identidad verificada
- **THEN** la titularidad es `UNKNOWN`, no se informa como anomalía y el portal le pide verificar la identidad

### Requirement: Vigencia, costo y bitácora de la consulta por placa
El sistema DEBE (SHALL) reutilizar sin nueva consulta los datos del convenio de un vehículo verificado mientras no superen `agreement-max-age` (90 días), mantener `document-max-age` para los datos de un PDF, limitar las consultas respondidas a un cupo diario por proveedor y registrar cada llamada sin guardar la respuesta cruda ni la identificación en claro.

#### Scenario: Consulta repetida dentro de 90 días
- **WHEN** se consulta un vehículo verificado con datos del convenio de hace 40 días
- **THEN** la respuesta trae `registryLookupStatus = REUSED`, no se llama al proveedor y no se consume cupo

#### Scenario: Datos del convenio vencidos
- **WHEN** los datos del convenio tienen más de 90 días
- **THEN** el vehículo puede consultarse otra vez o recibir un informe PDF

#### Scenario: Cada llamada en la bitácora
- **WHEN** se llama al proveedor
- **THEN** queda una fila en `vehicle.registry_lookup` con proveedor, resultado (`FOUND`, `NOT_FOUND` o `FAILED` con su código), clase de placa, propósito y quién la pidió

#### Scenario: Clase de la placa
- **WHEN** la placa normalizada es `CL123456`, `C123456`, `D12345` o `BCD123`
- **THEN** se envía la clase `CL`, `C`, `D` o `A` con el número `123456`, `123456`, `12345` o `BCD123`

#### Scenario: Proveedor elegido por configuración
- **WHEN** `motoria.vehicle.registry.provider` no es `credid`
- **THEN** se usa el adaptador de prueba, que nunca llama a la red

### Requirement: Consulta fresca para el traspaso
El sistema DEBE (SHALL) ofrecer al traspaso (F5) una consulta que ignore la vigencia de 90 días, antes de firmar y después de la compraventa, y DEBE (MUST) dejar documentado que embargos, anotaciones y colisiones no vienen de Credid.

#### Scenario: Refresco antes de firmar
- **WHEN** el traspaso pide `refreshForTransfer` con `TRANSFER_BEFORE` para un vehículo verificado hace 10 días por convenio
- **THEN** se consulta de nuevo el registro y la llamada queda en la bitácora con ese propósito
