# Design

## D1. Contrato de Credid para vehículos (tomado del gateway WSO2 de MNK)

- Fuente: el respaldo `CredidGateway`, secuencias `QueryCredid` y `QueryCredidCalidad`. El token no se copió.
- Llamada: `GET {url}?placa={número}&clase={clase}`.
  - El token va tal cual en `Authorization`, sin `Bearer`, con `Accept: application/json`. Es el mismo token que personas: `motoria.user.person-registry.credid.token` (`MOTORIA_CREDID_TOKEN`).
  - La URL de vehículos es otra y todavía no se conoce (puerta 0.1). Va en `motoria.vehicle.registry.credid.url` (`MOTORIA_CREDID_VEHICLE_URL`); vacía significa «no configurada» y el vendedor va al PDF.
  - Por decisión del dueño, solo se usa el ambiente de pruebas de Credid.
- Respuesta: objeto con `Dato`. Se toma esto:

  | Campo de Credid | Atributo |
  |---|---|
  | `Marca` | `BRAND` |
  | `Modelo` | `MODEL` |
  | `Anio` | `YEAR` |
  | `Color` | `COLOR` |
  | `NumeroVinCarroceria`, o `NumeroChasis` si falta | `VIN` |
  | `Motor.Numero` | `ENGINE_NUMBER` |
  | `Motor.Cilindrada` | `DISPLACEMENT_CC` |
  | `Motor.Combustible` | `FUEL` |
  | `Traccion` | `DRIVETRAIN` |
  | `Capacidad` | `CAPACITY` |
  | `InformacionPropietario[0].Nombre` | `OWNER_NAME` |
  | `InformacionPropietario[0].Identificacion` | `OWNER_ID_HASH`, solo como hash con llave (`IdNumberHasher`) |
  | `PoseeGarantias` y `TotalPrenda` | prenda (`RegistryEncumbrance` `PLEDGE`) |
  | `ConsultaId`, si viene | referencia del proveedor |

- No se guardan `ValorHacienda`, `Potencia`, `Cilindros`, `Motor.Marca`, `Motor.Modelo` ni `TipoCategoria`: Motoria no los muestra ni los necesita.
- Lectura tolerante, porque los tipos no están confirmados (puerta 0.3):
  - cada valor se lee con `JsonNode`, como texto o número; un decimal se escribe sin ceros de más (`2019.0` queda `2019`);
  - `PoseeGarantias` cuenta como verdadero si es booleano `true`, un número distinto de cero o uno de los textos `S`, `SI` (con o sin tilde), `TRUE`, `Y`, `YES` o `1`;
  - también hay prenda si `TotalPrenda`, sin comas, es mayor que cero.
- Varios dueños: como el gateway de MNK, se toma el primero de `InformacionPropietario`. La copropiedad queda por confirmar (puerta 0.3); mientras tanto, un copropietario que no sea el primero aparece como «dueño distinto».
- Se trata como **placa no encontrada**:
  - una respuesta 404 o vacía;
  - una respuesta sin `Dato`, o con `Dato` sin `Bien` o sin `Marca`;
  - un `Bien` que no es la placa consultada. Se compara normalizado (solo letras y dígitos) con la placa completa o con el número sin el prefijo de clase.
- Errores:
  - 401 o 403, o URL o token sin configurar: `VEHICLE_REGISTRY_NOT_CONFIGURED`;
  - otro estado fuera de 2xx, red, tiempo agotado (`motoria.vehicle.registry.credid.timeout`, PT15S) o JSON ilegible: `VEHICLE_REGISTRY_UNAVAILABLE`.

  El endpoint nunca los convierte en error HTTP (D5).
- Cliente: `java.net.http.HttpClient`, como `CredidPersonRegistryAdapter`.

## D2. Clase de la placa

`CredidPlateClass` aplica la regla de `seqBackGroundCredid` del gateway WSO2 (design D6 de `add-consulta-credid-personas`) sobre la placa normalizada:

| La placa empieza con | Clase | Número enviado |
|---|---|---|
| `CL` (y sigue algo) | CL | el resto |
| `C` (y sigue algo) | C | el resto |
| `D` seguida de un dígito | D | el resto |
| cualquier otra cosa | A | la placa completa |

Es una clase pura y probada aparte. Las expresiones del MOPT por tipo de placa reemplazarán esta regla cuando lleguen (puerta 0.4).

## D3. Selección del adaptador

`VehicleRegistryLookupPortProducer` lee `motoria.vehicle.registry.provider` (`MOTORIA_VEHICLE_REGISTRY_PROVIDER`, por defecto `stub`). Solo `credid` activa Credid; cualquier otro valor cae en `stub`, igual que en personas.

El stub nunca sale a la red:

| La placa termina en | Respuesta |
|---|---|
| `000` | no encontrada |
| `999` | datos completos con prenda (`TotalPrenda` 5000000) |
| cualquier otra cosa | datos completos ficticios |

El dueño del stub es `motoria.vehicle.registry.stub-owner-name` con la cédula ficticia `motoria.vehicle.registry.stub-owner-id` (por defecto `100000001`). En desarrollo se puede poner la cédula de un vendedor de prueba para ver la titularidad coincidente.

## D4. Orden del asistente de venta

Pedido del dueño: el vendedor escribe la placa, llena sus datos y sube las fotos, y recién entonces el sistema consulta Credid y valida.

| # | Paso del portal (`wizard.ts`) | Qué pasa |
|---|---|---|
| 1 | `plate` | `POST /vehicles` crea o reutiliza el vehículo; siempre sigue `data` |
| 2 | `data` | datos declarados; crea o actualiza el borrador del anuncio |
| 3 | `photos` | fotos del protocolo; «Continuar» lleva a `lookup` |
| 4 | `lookup` (nuevo, `RegistryLookupStep`) | llama a `POST /vehicles/{id}/registry-lookup` al entrar, una sola vez, mostrando «Consultando el Registro Nacional…» |
| 5 | `registry` (contingencia) | solo si el resultado no trae datos: aviso traducido y el paso de F1 para subir el PDF o pedir que Motoria lo obtenga |
| 6 | `status` | revisión del PDF o del caso del convenio, como en F1 |
| 7 | `vehicle` | ficha verificada, titularidad y gravámenes; «Confirmar» lleva a `submit` |
| 8 | `submit` | envío a revisión |

Qué hace el paso 4 con el resultado:

- `FOUND` o `REUSED` con el vehículo verificado: muestra el resultado y sigue a `vehicle`.
- `FOUND` con el vehículo en revisión: sigue a `status`.
- Si hay anomalías (D8), se muestran traducidas en el mismo paso.
- Un vehículo que ya está en `REGISTRY_IN_REVIEW` no se consulta: se manda a `status`.

Dependencias revisadas en F1:

- Crear el borrador del anuncio y subirle fotos **no exigía** el vehículo verificado:
  - `ListingPersistenceService.persistListing` solo pide que el vehículo exista;
  - `ListingPhotoService` solo pide el borrador en `DRAFT`.

  El reordenamiento no necesitó cambios grandes en `listing`.
- Enviar a revisión y publicar siguen exigiendo, en `ListingPublicationPolicy`, el vehículo `REGISTRY_VERIFIED` y la titularidad `MATCH`. No cambió.
- Único cambio en `listing`. Al crear el borrador se copian marca, modelo, año y combustible del vehículo. Si el vehículo aún no está verificado, esos datos quedan vacíos, y `ListingTrustService` solo actualiza el nivel de confianza. Por eso `ListingPublicationGuard.assertPublishable`, que corre al enviar a revisión y al publicar, vuelve a copiar esos datos de la ficha cuando el vehículo está verificado.
- Limitación aceptada: la sugerencia de título y descripción con IA del paso 2 se arma sin la ficha registral cuando el vehículo aún no está verificado. El vendedor puede pedirla de nuevo al volver al paso de datos.
- `resolveStep` manda a `data` si se pide `photos`, `lookup` o `submit` sin anuncio. «Mis anuncios» retoma en `data` o `photos` como antes.

## D5. Orquestación sin transacción alrededor de la llamada

`VehicleRegistryLookupService.lookup` no es transaccional. Sigue el patrón prepare/apply de `RegistryExtractionApplier` en `VehicleRegistryLookupApplier`:

1. **prepare** (transacción):
   - si el vehículo está en `REGISTRY_IN_REVIEW`, responde 409 `VEHICLE_INVALID_STATE`, porque hay un caso esperando al analista;
   - si está verificado y vigente según su fuente (D7), responde `REUSED` sin pagar;
   - si el país no es CR, responde `NOT_CONFIGURED`;
   - si ya se agotó el cupo (D9), responde `QUOTA_EXHAUSTED`;
   - en otro caso, sigue con la placa.
2. **Llamada a Credid**, sin transacción. Una `VehicleRegistryUnavailableException` u otra excepción en tiempo de ejecución se registra como `FAILED` con su código y responde `NOT_CONFIGURED` o `UNAVAILABLE`. La cédula del dueño se convierte en hash aquí, fuera de la transacción; si falta la llave del hash, el dato se descarta y el caso va al analista (`OWNER_ID_HASH` es obligatorio).
3. **apply** (transacción): guarda la fila de bitácora, aplica los datos (D6) y detecta anomalías (D8).
   - Si entretanto el vehículo pasó a revisión (por ejemplo, se subió un PDF), los datos no se aplican y responde `FOUND`. El portal lo manda a `status`.

Resultado:

- La respuesta es `RegistryLookupResponse`: `registryLookupStatus`, `errorCode`, `anomalies` y el `VehicleResponse` actualizado.
- No encontrada, Credid caído, sin configurar o sin cupo: el vehículo queda como estaba (normalmente `REGISTRY_PENDING`) y la respuesta es 200.
- El flujo del PDF no cambió: `attachRegistryDocument` acepta `PENDING`, `REJECTED` o `VERIFIED` vencido, ahora con la vigencia de su fuente.
  - Caso borde: una solicitud «que Motoria lo obtenga» abierta cuando una consulta posterior ya verificó el vehículo por convenio. Al subir el informe, el operador recibe `VEHICLE_INVALID_STATE` (409), igual que en F1 con un vehículo verificado vigente. Cerrar esas solicitudes queda pendiente.
- Pueden consultar los roles del adjunto del documento (SELLER, DEALER, ADMIN). Igual que el adjunto, no se exige ser quien registró la placa, porque el vehículo es único por placa y lo comparten los vendedores. La titularidad se valida al enviar el anuncio.

## D6. Datos del convenio en el vehículo

`Vehicle.applyAgreementLookup` acepta cualquier estado salvo `REGISTRY_IN_REVIEW`:

- deja la fuente en `AGREEMENT` y limpia documento, fecha del documento, inferencia y motivo de rechazo;
- guarda los atributos con fuente `REGISTRY_AGREEMENT`, sin medio, página ni confianza. Son columnas que aceptan nulos en V4, y la confianza no aplica a un dato del registro;
- reemplaza los valores existentes en su lugar, para respetar la llave única `vehicle_id, attr_key`, y borra los oficiales que no llegaron, como `applyExtraction`;
- reemplaza los gravámenes por la prenda informada. La descripción guarda solo el dato del registro (`TotalPrenda` tal cual, o nada si no viene), sin frases fijas: el tipo lo traduce la UI y el origen «convenio» lo muestra el backoffice.

Decisión:

- Si están todos los atributos de `motoria.vehicle.registry.required-attributes` y `motoria.vehicle.registry.agreement-auto-approve=true` (por defecto), el vehículo pasa a `REGISTRY_VERIFIED`. Toma `registryCheckedAt` = ahora, calcula los bloqueos con `TransferBlockPolicy` y publica `vehicle.registry.completed`, igual que F1.
  - La aprobación automática del PDF (`auto-approve-enabled`) sigue apagada; esta es otra bandera, porque el dato del convenio no pasa por IA.
- Si falta un obligatorio, el caso va al analista con `ReviewReason.MISSING_REQUIRED`; si la bandera está apagada, con el motivo nuevo `AGREEMENT_REVIEW`. En ambos casos se publica `vehicle.registry.review.required`, sin inferencia.
- La bandeja de revisión ya soporta casos sin documento:
  - `pages()` devuelve una lista vacía sin documento;
  - `recordHumanDecision` ignora una inferencia nula;
  - `approve()` usa «ahora» sin fecha de documento.
- El detalle de la revisión agrega `registrySource`, y el backoffice muestra «Datos del Registro Nacional por convenio, sin documento».

## D7. Origen de los datos y vigencia (caché interna de 90 días)

- El resultado vive en el propio vehículo: la columna nueva `vehicle.registry_source` (`DOCUMENT` o `AGREEMENT`, con `CHECK`).
  - Se eligió una columna en lugar de derivarlo de los atributos, porque un analista puede corregir valores (fuente `ANALYST`) y se perdería el origen.
  - Las filas previas quedan en `NULL` y se tratan como `DOCUMENT`.
- `attachRegistryDocument` fija `DOCUMENT` y `applyAgreementLookup` fija `AGREEMENT`.
- `isRegistryStale` y `canReceiveRegistryDocument` tienen sobrecargas con las dos vigencias:
  - el documento sigue con `document-max-age` (P30D);
  - el convenio usa `agreement-max-age` (P90D).

  Las firmas de F1 con una sola vigencia se conservan.
- Un vehículo verificado y vigente no se vuelve a consultar: responde `REUSED`, sin cupo ni bitácora. La vigencia se cuenta desde `registryCheckedAt` (90 días si los datos son del convenio, 30 si son de un documento).

## D8. Anomalías y comunicación al vendedor

`RegistryAnomalyPolicy` compara los datos registrales con el vendedor que consultó:

- `OWNER_MISMATCH`: `OwnershipPolicy` da `MISMATCH` entre `OWNER_ID_HASH` y el hash de la cédula verificada del vendedor (`UserIdentityPort`).
  - Si el vendedor aún no tiene cédula verificada, la titularidad es `UNKNOWN` y no cuenta como anomalía; se le pide verificar la identidad, como en F1.
  - Un dueño distinto no impide que el vehículo quede verificado, porque el dato del registro es cierto. Lo que no se puede es enviar ni publicar el anuncio (`LISTING_SELLER_NOT_OWNER`), por la regla de titularidad de F1.
- `PLEDGE`: el registro informa prenda. Bloquea el traspaso, no la publicación.
- Lo declarado frente al registro no se compara. F1 tiene la entidad `RegistryDiscrepancy`, pero ningún código la llena, y los datos que declara el vendedor (precio, kilometraje, provincia, transmisión, equipo, mantenimiento) no tienen par en el registro. Marca, modelo, año y combustible no se declaran: se copian de la ficha. Queda para cuando haya datos declarados comparables (F2 agrega `RegistryDiscrepancy.source` para lo observado en la inspección).

Comunicación:

- Si hay alguna anomalía, se publica `vehicle.registry.anomaly.detected` por el outbox, en la misma transacción. El payload lleva `vehicleId`, `sellerId` y los códigos, sin placa, nombre ni cédula.
- `NotificationEventConsumer` lo recibe por la cola existente `notification.vehicle-registry-events`, que suma la clave de enrutamiento, y `NotificationService.handleVehicleRegistryAnomaly` prepara el aviso.
  - Como las demás notificaciones de F1, hoy solo se registra en el log. El canal de envío (correo o push) es la puerta 0.5.
- SmallRye declara el enlace de la clave nueva al arrancar si la topología no está preaprovisionada. Si lo está, hay que agregarlo a mano (puerta 0.6).
- Se avisa tanto con `FOUND` como con `REUSED`. Así un segundo vendedor que reutiliza la ficha de otra persona también recibe la comunicación.
- El portal solo consulta una vez por visita al paso. Aun así, quitar avisos repetidos le toca al canal de notificaciones cuando exista.

## D9. Bitácora y cupo

`vehicle.registry_lookup` guarda una fila por llamada a Credid:

- proveedor, propósito (`SELLER_LOOKUP`, `TRANSFER_BEFORE`, `TRANSFER_AFTER` o `ANALYST`);
- resultado (`FOUND`, `NOT_FOUND` o `FAILED`) y código de error;
- clase de placa, referencia del proveedor, `requested_by` y `checked_at`;
- columnas de auditoría.

No guarda la respuesta cruda ni la cédula. `ANALYST` queda reservado: no hay consulta del backoffice por placa en este cambio.

Cupo:

- `motoria.vehicle.registry.daily-quota` (200), contado por proveedor y día de Costa Rica (`motoria.vehicle.registry.quota-zone`).
- Solo cuentan `FOUND` y `NOT_FOUND`, las respuestas que factura el proveedor. Las fallas no consumen cupo; las filas del stub tampoco cuentan para el de Credid.
- Hay índices por proveedor y fecha (cupo) y por vehículo y fecha (historial).
- Igual que en personas, contar y llamar no es atómico: en un pico el cupo se puede pasar por unas pocas consultas.

## D10. Traspaso (F5): consulta fresca y lo que Credid no informa

- `VehicleRegistryLookupService.refreshForTransfer(vehicleId, purpose)` es el gancho para F5:
  - solo acepta `TRANSFER_BEFORE` y `TRANSFER_AFTER`;
  - ignora la vigencia de 90 días y siempre consulta;
  - no avisa anomalías, porque F5 decidirá qué hacer con ellas;
  - debe llamarse fuera de una transacción.
- F5 lo usará antes de firmar, para confirmar dueño y prenda al día, y después de la compraventa, para dejar al dueño nuevo en el vehículo. No se crearon endpoints de F5.
- Credid solo informa garantías y prenda. **Embargos, anotaciones y colisiones no vienen de Credid.** Un vehículo verificado por convenio puede tenerlos sin que Motoria lo sepa. Por eso:
  - la publicación sigue igual que en F1, porque esos gravámenes no la bloquean;
  - el traspaso (F5) deberá exigir una verificación fresca que los cubra (informe registral del día o el servicio que el Registro ofrezca).

## D11. Privacidad y logs

- La cédula del dueño solo vive en memoria (`VehicleRegistryRecord`, cuyo `toString` no la imprime) hasta que se convierte en hash.
- Los logs del servicio llevan el id del vehículo y el código de error; nunca la placa, la cédula ni el nombre.
- El evento de anomalía tampoco los lleva.

## D12. Migración

V9, en el rango de F1 (V1 a V19): V8 es la consulta de personas, y F2 usa de V20 en adelante. Revisado en la rama F2: su V21 toca `registry_discrepancy` y no choca con estas columnas.

- Agrega `vehicle.vehicle.registry_source`, con `CHECK` y nulo para las filas previas.
- Crea `vehicle.registry_lookup` con:
  - llave foránea a `vehicle.vehicle`, dentro del esquema;
  - `CHECK` de propósito, resultado y clase;
  - auditoría e índices.
- `review_reason` no tiene `CHECK` en V4, así que `AGREEMENT_REVIEW` no requiere cambiar el esquema.

## D13. Personas a 90 días

Por decisión del dueño, la caché del registro de personas también pasa a 90 días (`motoria.user.person-registry.cache-ttl=P90D`, con el valor por defecto en `PersonRegistryService`). Se actualizó el design D3 de `add-consulta-credid-personas`.
