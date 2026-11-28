# Tasks

Marcas: (A) agente, (B) puerta del dueño. Una marca [x] significa «escrito», nunca «compilado ni probado». Migración: **V9**, dentro del rango de F1 (V1 a V19).

Rutas de evidencia relativas a `backend/src/main/java/ai/motoria/` (código), `backend/src/main/resources/` (migraciones y configuración), `backend/src/test/java/ai/motoria/` (pruebas) y `frontend/` (web).

## 0. Puertas

- [ ] 0.1 (B) URL del servicio de vehículos de Credid en el ambiente de pruebas, en `MOTORIA_CREDID_VEHICLE_URL`, más `MOTORIA_VEHICLE_REGISTRY_PROVIDER=credid`. Usa el mismo token de pruebas que personas (`MOTORIA_CREDID_TOKEN`, nunca en el repositorio). Sin la URL, el vendedor va siempre al PDF (`NOT_CONFIGURED`). Decisión del dueño (2026-11-26): solo el ambiente de pruebas hasta el contrato propio (puerta 0.1 de `add-consulta-credid-personas`).
- [ ] 0.2 (B) Base legal (Ley 8968) para guardar el nombre del dueño registral y comparar su identificación con la del vendedor. Lo mismo que hace F1 con el PDF, ahora con datos del convenio.
- [ ] 0.3 (B) Confirmar con Credid (design D1):
  - los tipos de `Anio`, `PoseeGarantias` y `TotalPrenda`;
  - qué responde cuando la placa no existe;
  - si `Bien` viene con el prefijo de clase;
  - cómo informa la copropiedad (hoy se toma el primer dueño).
- [ ] 0.4 (B) Expresiones del MOPT por tipo de placa, que reemplazarán la regla de clase de MNK (design D2).
- [ ] 0.5 (B) Canal de envío de las notificaciones (correo o push). Hoy `NotificationService` solo registra el aviso de anomalía en el log, como el resto de las notificaciones de F1 (design D8).
- [ ] 0.6 (B) RabbitMQ: confirmar que la cola `notification.vehicle-registry-events` queda enlazada a la clave nueva `vehicle.registry.anomaly.detected`. SmallRye declara el enlace al arrancar; si la topología está preaprovisionada, hay que agregarlo a mano.
- [ ] 0.7 (B) Probar contra Credid de pruebas con placas reales: con prenda, sin prenda, inexistente y de cada clase (CL, C, D, A).

## 1. Backend

- [x] 1.1 (A) Migración V9: `vehicle.vehicle.registry_source` y la tabla `vehicle.registry_lookup` (design D7, D9 y D12). Evidencia: `db/migration/V9__vehicle_registry_lookup.sql`.
- [x] 1.2 (A) Dominio. Evidencia:
  - `vehicle/domain/RegistrySource.java`, `RegistryLookup.java`, `RegistryLookupPurpose.java`, `RegistryLookupOutcome.java`, `RegistryLookupStatus.java`, `RegistryAnomaly.java`, `RegistryAnomalyPolicy.java`;
  - `ReviewReason.AGREEMENT_REVIEW`;
  - `Vehicle`: `registrySource`, `applyAgreementLookup`, `canApplyAgreementLookup`, `hasFreshAgreementData`, `registryMaxAge`, y sobrecargas de `isRegistryStale`, `canReceiveRegistryDocument` y `attachRegistryDocument` con las dos vigencias.
- [x] 1.3 (A) Puerto y adaptadores (design D1 a D3). Evidencia: `vehicle/integration/VehicleRegistryLookupPort.java`, `VehicleRegistryRecord.java`, `VehicleRegistryUnavailableException.java`, `CredidPlateClass.java`, `CredidVehicleRegistryAdapter.java`, `StubVehicleRegistryAdapter.java` y `VehicleRegistryLookupPortProducer.java`.
- [x] 1.4 (A) Orquestación prepare, llamada y apply, sin transacción alrededor de la llamada; reutilización a 90 días, cupo, bitácora, aprobación automática y anomalías (design D5 a D9). Evidencia:
  - `vehicle/service/VehicleRegistryLookupService.java` (`lookup`, `refreshForTransfer`);
  - `VehicleRegistryLookupApplier.java` (`prepare`, `recordFailure`, `apply`, `reportAnomalies`);
  - `RegistryLookupResult.java`;
  - `vehicle/repository/RegistryLookupRepository.java` (`countAnsweredSince`);
  - `VehicleRegistrySettings` (`agreementMaxAge`, `agreementAutoApprove`, `dailyQuota`, `quotaZone`);
  - `VehicleService.attach`, que usa las dos vigencias.
- [x] 1.5 (A) Endpoint `POST /vehicles/{vehicleId}/registry-lookup` (SELLER, DEALER y ADMIN; auditado por `@Audited` en el servicio). Evidencia: `vehicle/rest/VehicleResource.java` (`lookUpRegistry`) y `vehicle/dto/RegistryLookupResponse.java`.
- [x] 1.6 (A) Revisión registral sin documento. Evidencia: `vehicle/dto/RegistryReviewDetailResponse.java` (`registrySource`) y `vehicle/mapper/VehicleMapper.java` (`toReviewDetail`).
- [x] 1.7 (A) Evento y comunicación de anomalías (design D8). Evidencia:
  - `common/event/EventType.java` (`VEHICLE_REGISTRY_ANOMALY_DETECTED`);
  - `vehicle/event/VehicleRegistryAnomalyPayload.java` y `VehicleEventProducer.registryAnomalyDetected`;
  - `notification/event/NotificationEventConsumer.java` (`onVehicleRegistryEvent`);
  - `notification/dto/VehicleRegistryAnomalyNotificationCommand.java`;
  - `notification/service/NotificationService.java` (`handleVehicleRegistryAnomaly`);
  - `application.properties` (clave de enrutamiento en `notification-vehicle-registry-events-in`).
- [x] 1.8 (A) Borrador y fotos antes de la verificación: al enviar a revisión o publicar se vuelve a copiar la ficha registral (design D4). Evidencia: `listing/service/ListingPublicationGuard.java` (`assertPublishable`).
- [x] 1.9 (A) Códigos de error y configuración. Evidencia: `vehicle/exception/VehicleErrorCodes.java` (`VEHICLE_REGISTRY_NOT_CONFIGURED`, `VEHICLE_REGISTRY_UNAVAILABLE`, `VEHICLE_REGISTRY_QUOTA_EXHAUSTED`) y `application.properties` (`motoria.vehicle.registry.provider`, `credid.url`, `credid.timeout`, `agreement-max-age`, `agreement-auto-approve`, `daily-quota`, `quota-zone`).
- [x] 1.10 (A) Personas a 90 días (design D13). Evidencia: `application.properties` (`motoria.user.person-registry.cache-ttl=P90D`), `user/service/PersonRegistryService.java` (`defaultValue = "P90D"`) y `openspec/changes/add-consulta-credid-personas` (proposal, design D3, spec y tasks).

## 2. Frontend

- [x] 2.1 (A) Tipos y hook. Evidencia: `packages/api/src/types.ts` (`REGISTRY_SOURCES`, `REGISTRY_LOOKUP_STATUSES`, `REGISTRY_ANOMALIES`, `RegistryLookupResult`, `AGREEMENT_REVIEW` en `REVIEW_REASONS`, `registrySource` en `RegistryReviewDetail`) y `packages/api/src/hooks.ts` (`useRegistryLookup`).
- [x] 2.2 (A) Asistente reordenado: placa, datos, fotos, consulta, PDF de contingencia, estado, ficha y envío (design D4). Evidencia:
  - `apps/portal-cliente/src/pages/sell/wizard.ts` (`SELL_STEPS`) y `SellPage.tsx` (`resolveStep`, paso `lookup`);
  - `PlateStep.tsx`, que siempre sigue a `data`;
  - `DeclaredDataStep.tsx`, que vuelve a `plate`;
  - `PhotosStep.tsx`, que continúa a `lookup`;
  - `VehicleReviewStep.tsx`, que continúa a `submit`;
  - `SubmitStep.tsx`, que vuelve a `vehicle`.
- [x] 2.3 (A) Paso de consulta al registro, con anomalías y contingencia del PDF. Evidencia: `apps/portal-cliente/src/pages/sell/RegistryLookupStep.tsx`.
- [x] 2.4 (A) Backoffice: revisión de un caso del convenio sin documento. Evidencia: `apps/backoffice/src/pages/RegistryReviewDetailPage.tsx` (`agreement`, `agreementTitle`, `agreementSubtitle`, `agreementNoDocument`).
- [x] 2.5 (A) Textos en español e inglés. Evidencia: `packages/i18n/src/locales/es.ts` y `en.ts`:
  - `sell.steps.lookup` y `sell.lookup.*`;
  - `reviewReason.AGREEMENT_REVIEW`;
  - `backoffice.review.agreement*`;
  - `errors.VEHICLE_REGISTRY_*`.

  `attributeSource.REGISTRY_AGREEMENT` ya existía.

## 3. Pruebas (escritas, no ejecutadas)

- [x] 3.1 (A) Lectura de la respuesta de Credid. Evidencia: `vehicle/integration/CredidVehicleRegistryAdapterTest.java`. Cubre:
  - encontrado, sin `Dato`, 404, 401, 403, 500, ilegible, `Bien` distinto y placa sin prefijo de clase;
  - variantes de `PoseeGarantias` y `TotalPrenda`, y decimales;
  - URL sin configurar.
- [x] 3.2 (A) Regla de clase de placa. Evidencia: `vehicle/integration/CredidPlateClassTest.java`.
- [x] 3.3 (A) Dominio del vehículo: aplicar el convenio, aprobación con prenda que bloquea el traspaso, reemplazo de datos del documento, bloqueo en revisión, vigencia por fuente y PDF sobre convenio vencido. Evidencia: `vehicle/domain/VehicleTest.java`:
  - `agreementDataShouldUseTheAgreementSourceWithoutDocument`;
  - `approvedAgreementDataShouldBeCheckedNowAndPledgeBlocksTransfer`;
  - `agreementDataShouldReplaceDocumentValuesAndDropTheOnesNotReceived`;
  - `agreementDataCannotBeAppliedWhileWaitingForTheAnalyst`;
  - `agreementDataShouldStayFreshForItsOwnMaximumAge`;
  - `documentDataShouldKeepTheDocumentMaximumAge`;
  - `documentCanReplaceStaleAgreementData`.
- [x] 3.4 (A) Orquestación: aprobación automática, prenda, dueño distinto, aprobación apagada, faltante, no encontrada, falla, reutilización a 90 días, refresco del traspaso, cupo agotado y vehículo en revisión. Evidencia: `vehicle/service/VehicleRegistryLookupApplierTest.java`.
- [x] 3.5 (A) Roles del endpoint nuevo y respuesta 200 con Credid caído. Evidencia: `vehicle/rest/VehicleResourceTest.java`:
  - `sellerCanLookUpTheRegistryByPlate`;
  - `registryUnavailableShouldNotBeAServerError`;
  - `analystCannotLookUpTheRegistryAsSeller`;
  - `buyerCannotLookUpTheRegistry`;
  - `anonymousCannotLookUpTheRegistry`.
- [x] 3.6 (A) Despacho del evento de anomalía. Evidencia: `notification/event/NotificationEventConsumerTest.java` (`shouldDispatchVehicleRegistryAnomalyToTheSeller`).
- [ ] 3.7 (A) Prueba de integración contra PostgreSQL de V9, el cupo y la bitácora: pendiente; el proyecto aún no tiene pruebas con base de datos.
- [ ] 3.8 (A) Pruebas del portal para `RegistryLookupStep`: pendiente; el frontend aún no tiene pruebas.

## 4. Fuera de este cambio

- Endpoints y flujo del traspaso (F5). Solo queda el gancho `refreshForTransfer` (design D10).
- Comparación de lo declarado frente al registro (`RegistryDiscrepancy`). F1 no la tiene y lo declarado no tiene par en el registro (design D8).
- Consulta por placa desde el backoffice (propósito `ANALYST` reservado).
