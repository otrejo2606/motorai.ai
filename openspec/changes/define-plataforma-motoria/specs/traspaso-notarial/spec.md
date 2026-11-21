## ADDED Requirements

### Requirement: Expediente de traspaso
El sistema DEBE (SHALL) abrir un expediente de traspaso cuando comprador y vendedor acuerdan la venta, con las partes verificadas, la ficha registral actualizada, el precio, la forma de pago, los comprobantes de marchamo y RTV, y el estado de multas.

#### Scenario: Requisito faltante
- **WHEN** falta el comprobante de marchamo al día
- **THEN** el expediente queda en `INCOMPLETE` con la lista de faltantes visible para ambas partes

### Requirement: Notario aliado con fe pública
El sistema DEBE (SHALL) asignar el expediente a un notario aliado habilitado y al día ante la Dirección Nacional de Notariado; solo el notario o su asistente digital designado DEBE (MUST) aprobar el borrador, autorizar la escritura y presentarla al Registro, por la Ventanilla Digital del Registro Nacional cuando el documento sea digital.

#### Scenario: Notario inhabilitado
- **WHEN** la habilitación del notario está vencida o suspendida
- **THEN** el sistema no le asigna expedientes y alerta al administrador

### Requirement: Borrador de escritura generado
El sistema DEBE (SHALL) generar el borrador de la escritura de compraventa desde plantillas versionadas y los datos del expediente, para revisión y edición del notario.

#### Scenario: Notario corrige el borrador
- **WHEN** el notario edita el borrador
- **THEN** el sistema guarda la versión, el autor y el cambio

### Requirement: Firma digital
El sistema DEBE (SHALL) recoger la firma de las partes y del notario con firma digital certificada (BCCR en Costa Rica) o, cuando una parte no la tenga, coordinar la firma presencial ante el notario; NO DEBE (MUST NOT) sustituir la firma certificada por firma simple.

#### Scenario: Parte sin firma digital
- **WHEN** el comprador no tiene firma digital
- **THEN** el sistema agenda la firma presencial y el expediente sigue el flujo híbrido

### Requirement: Costos transparentes y pagos
El sistema DEBE (SHALL) calcular y mostrar antes de firmar el impuesto de traspaso sobre el valor fiscal, los timbres, los honorarios notariales según el arancel y la tarifa de Motoria, con tasas y tablas configurables, y registrar quién paga cada concepto.

#### Scenario: Cambio de tabla de valor fiscal
- **WHEN** Hacienda publica una tabla nueva y el administrador la carga
- **THEN** los expedientes nuevos usan la tabla vigente y los firmados conservan la usada

### Requirement: Pago protegido
El sistema DEBE (SHALL) permitir que el precio de venta se pague por un mecanismo protegido (custodia con una entidad financiera aliada, por verificar en design D7.7, o transferencia confirmada), sin que Motoria custodie fondos por sí misma, y liberar el dinero al vendedor solo cuando el traspaso esté presentado o inscrito, según la regla configurada.

#### Scenario: Traspaso rechazado por el Registro
- **WHEN** el Registro devuelve el documento con defectos
- **THEN** el dinero sigue en custodia y el notario recibe la tarea de subsanar

### Requirement: Seguimiento hasta la inscripción
El sistema DEBE (SHALL) seguir el estado de la presentación al Registro hasta la inscripción y notificar a las partes en cada cambio; al inscribirse, el anuncio pasa a `SOLD`.

#### Scenario: Inscripción
- **WHEN** el Registro inscribe el traspaso
- **THEN** el sistema publica `notary.registry.inscribed`, actualiza el propietario en la ficha y cierra el expediente
