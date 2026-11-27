## ADDED Requirements

### Requirement: Cotejo de la identidad con el registro de personas
El sistema DEBE (SHALL) consultar el registro de personas (Credid) con la cédula del perfil al enviarse una verificación de identidad y DEBE (MUST) mostrar al analista el resultado (`MATCH`, `NAME_MISMATCH`, `NOT_FOUND`, `DOCUMENT_EXPIRED`, `UNAVAILABLE` o `NOT_APPLICABLE`) sin aprobar ni rechazar el caso por sí mismo.

#### Scenario: Nombre y documento coinciden
- **WHEN** un vendedor con cédula física y nombre «María Pérez» envía su verificación y el registro devuelve «MARIA JOSE PEREZ ROJAS» con la cédula vigente
- **THEN** el caso queda en revisión con `registryStatus = MATCH` y el analista ve el nombre del registro y la fecha de vencimiento

#### Scenario: Documento vencido
- **WHEN** el registro informa la cédula con vencimiento anterior a hoy
- **THEN** el caso queda con `registryStatus = DOCUMENT_EXPIRED`

#### Scenario: El registro no responde
- **WHEN** el registro falla, se agota el tiempo o se alcanzó el cupo diario
- **THEN** la verificación se envía igual, queda con `registryStatus = UNAVAILABLE` y el analista puede volver a consultar

#### Scenario: Pasaporte
- **WHEN** el perfil tiene pasaporte
- **THEN** no se consulta el registro y el caso queda con `registryStatus = NOT_APPLICABLE`

### Requirement: Consulta de personas por documento en el backoffice
El sistema DEBE (SHALL) permitir a los roles BACKOFFICE y ADMIN consultar en el registro a quién pertenece una cédula física, jurídica o DIMEX, con un motivo obligatorio, y DEBE (MUST) devolver solo nombre, tipo de persona, vencimiento del documento y nacionalidad.

#### Scenario: Consulta con motivo
- **WHEN** un analista consulta la cédula 1-0234-0567 con el motivo «Revisión de un caso de fraude»
- **THEN** recibe el nombre y el vencimiento del documento, y la consulta queda en la bitácora con su usuario, el propósito `BACKOFFICE_LOOKUP` y el motivo

#### Scenario: Sin permiso
- **WHEN** un usuario con rol SELLER llama la consulta
- **THEN** el servidor responde 403

#### Scenario: Pasaporte
- **WHEN** el analista consulta un pasaporte
- **THEN** el servidor responde 422 con `code = PERSON_REGISTRY_ID_TYPE_UNSUPPORTED`

### Requirement: Costo y privacidad de las consultas al registro
El sistema DEBE (SHALL) reutilizar durante `cache-ttl` la consulta vigente de la misma cédula, limitar las consultas pagadas a un cupo diario, registrar cada uso (pagado o reutilizado) y NO DEBE (MUST NOT) guardar la cédula en claro ni datos del registro distintos de nombre, tipo de persona, vencimiento del documento y nacionalidad.

#### Scenario: Consulta repetida
- **WHEN** se consulta dos veces la misma cédula dentro de 90 días
- **THEN** la segunda reutiliza la primera (`fromCache = true`), no llama al proveedor y queda igual en la bitácora

#### Scenario: Cupo agotado
- **WHEN** ya se hicieron en el día tantas consultas pagadas como `daily-quota`
- **THEN** la consulta del backoffice responde 429 con `code = PERSON_REGISTRY_QUOTA_EXHAUSTED`

#### Scenario: Proveedor elegido por configuración
- **WHEN** `motoria.user.person-registry.provider` no es `credid`
- **THEN** se usa el adaptador de prueba, que nunca llama a la red
