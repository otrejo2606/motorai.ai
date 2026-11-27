# Tasks

Marcas: (A) agente, (B) puerta del dueño. Una marca [x] significa «escrito», nunca «compilado ni probado». Migración: **V8**, dentro del rango de F1 (V1 a V19).

Rutas de evidencia relativas a `backend/src/main/java/ai/motoria/` (código), `backend/src/main/resources/` (migraciones y configuración), `backend/src/test/java/ai/motoria/` (pruebas) y `frontend/` (web).

## 0. Puertas

- [ ] 0.1 (B) Contrato propio de Motoria con Credid (costo por consulta y cupo diario). Decisión del dueño (2026-11-26): se hará en su momento; mientras tanto solo se usa el ambiente de pruebas de Credid (`https://ws.credid.net/wstest/api/Reporte`, valor por defecto de `motoria.user.person-registry.credid.url`).
- [ ] 0.2 (B) Para probar contra Credid: token del ambiente de pruebas en `MOTORIA_CREDID_TOKEN` (variable de entorno local o gestor de secretos, nunca en el repositorio) y `MOTORIA_PERSON_REGISTRY_PROVIDER=credid`. Con el contrato (0.1): URL de producción en `MOTORIA_CREDID_URL` y token propio. El token del gateway MNK quedó en su historial de git: rotarlo.
- [ ] 0.3 (B) Base legal de la consulta del backoffice (Ley 8968): para qué casos se permite y si el consentimiento de tratamiento del perfil cubre el cotejo en la verificación.
- [ ] 0.4 (B) Confirmar con Credid qué responde cuando la cédula no existe y el formato de sus fechas (design D1).

## 1. Backend

- [x] 1.1 (A) Migración V8: `usr.person_registry_check`, `usr.person_registry_access` y columnas `registry_status` y `registry_check_id` en `usr.identity_verification`. Evidencia: `db/migration/V8__usr_person_registry.sql`.
- [x] 1.2 (A) Dominio. Evidencia: `user/domain/PersonRegistryCheck.java`, `PersonRegistryAccess.java`, `PersonRegistryPurpose.java`, `RegistryMatchStatus.java`; `IdentityVerification.recordRegistryCheck`.
- [x] 1.3 (A) Puerto y adaptadores (design D1 y D2). Evidencia: `user/integration/PersonRegistryPort.java`, `PersonRegistryRecord.java`, `PersonRegistryUnavailableException.java`, `CredidPersonRegistryAdapter.java`, `StubPersonRegistryAdapter.java`, `PersonRegistryPortProducer.java`.
- [x] 1.4 (A) Servicio con caché, cupo, bitácora y cotejo (design D3 y D4). Evidencia: `user/service/PersonRegistryService.java`, `RegistryNameMatcher.java`; repositorios `user/repository/PersonRegistryCheckRepository.java`, `PersonRegistryAccessRepository.java`.
- [x] 1.5 (A) Cotejo al enviar y repetición del cotejo por el analista. Evidencia: `user/service/IdentityVerificationService.java` (`submit`, `recheckRegistry`), `user/rest/BackofficeIdentityVerificationResource.java` (`POST /{id}/registry-check`), `user/mapper/UserMapper.java` y `user/dto/BackofficeIdentityVerificationResponse.java` (campos `registry*`).
- [x] 1.6 (A) Consulta del backoffice (design D5). Evidencia: `user/rest/BackofficePersonRegistryResource.java`, `user/dto/PersonRegistryLookupRequest.java`, `PersonRegistryLookupResponse.java`.
- [x] 1.7 (A) Códigos de error y configuración. Evidencia: `user/exception/UserErrorCodes.java` (`PERSON_REGISTRY_*`), `application.properties` (`motoria.user.person-registry.*`).
- [x] 1.8 (A) Caché de personas a 90 días (design D3; decisión del dueño 2026-11-26). Evidencia: `application.properties` (`motoria.user.person-registry.cache-ttl=P90D`), `user/service/PersonRegistryService.java` (`defaultValue = "P90D"`).

## 2. Frontend (backoffice)

- [x] 2.1 (A) Tipos y hooks. Evidencia: `packages/api/src/types.ts` (`REGISTRY_MATCH_STATUSES`, `PERSON_TYPES`, `REGISTRY_ID_TYPES`, `PersonRegistryLookup*`, campos `registry*` de `BackofficeIdentityVerification`), `packages/api/src/hooks.ts` (`useRecheckIdentityRegistry`, `usePersonRegistryLookup`).
- [x] 2.2 (A) Resultado del registro en la tarjeta de verificación. Evidencia: `apps/backoffice/src/components/RegistryCheckPanel.tsx`, `apps/backoffice/src/pages/IdentityVerificationsPage.tsx`.
- [x] 2.3 (A) Página «Consulta de personas», ruta y menú. Evidencia: `apps/backoffice/src/pages/PersonRegistryPage.tsx`, `apps/backoffice/src/App.tsx`.
- [x] 2.4 (A) Textos en español e inglés. Evidencia: `packages/i18n/src/locales/es.ts` y `en.ts` (`registryMatch`, `personType`, `backoffice.nav.personRegistry`, `backoffice.identity.registry`, `backoffice.personRegistry`, `validation.reasonMin10`, `validation.registryIdNumber`, `errors.PERSON_REGISTRY_*`).

## 3. Pruebas (escritas, no ejecutadas)

- [x] 3.1 (A) Lectura de la respuesta de Credid y códigos de error. Evidencia: `user/integration/CredidPersonRegistryAdapterTest.java`.
- [x] 3.2 (A) Comparación de nombres. Evidencia: `user/service/RegistryNameMatcherTest.java`.
- [x] 3.3 (A) El cotejo solo se guarda con el caso en revisión. Evidencia: `user/domain/IdentityVerificationTest.java` (`registryCheckIsRecordedOnlyWhileWaitingForReview`).
- [x] 3.4 (A) Roles y validación de los endpoints. Evidencia: `user/rest/UserResourcesTest.java` (`sellerCannotLookUpPeopleInTheRegistry`, `registryLookupWithoutReasonIsRejected`, `analystCanLookUpAPersonInTheRegistry`, `sellerCannotRecheckTheRegistryOfAVerification`).
- [ ] 3.5 (A) Prueba de integración contra PostgreSQL de V8, la caché y el cupo: pendiente; el proyecto aún no tiene pruebas con base de datos.

## 4. Fuera de este cambio

- Consulta de vehículos por placa en Credid: es el cambio `add-consulta-credid-vehiculos`, con el contrato del gateway WSO2 de MNK. Usa la regla de la placa del design D6 de este cambio (prefijos `CL`, `C` y `D` seguido de dígito).
