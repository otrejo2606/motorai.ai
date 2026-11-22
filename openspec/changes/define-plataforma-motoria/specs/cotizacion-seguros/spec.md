## ADDED Requirements

### Requirement: Intermediación exclusiva por corredora licenciada
El sistema DEBE (SHALL) obtener cotizaciones y emitir pólizas solo a través de NEXUS Broker (corredora licenciada SUGESE) por un puerto de integración, y NO DEBE (MUST NOT) presentarse como aseguradora ni como intermediario de seguros.

#### Scenario: Cotización solicitada
- **WHEN** un comprador pide cotizar el seguro de un vehículo
- **THEN** el sistema envía la solicitud a NEXUS con `insurance.quote.requested` y muestra que la intermediación la realiza NEXUS Broker con su número de licencia

### Requirement: Cotización con varias aseguradoras
El sistema DEBE (SHALL) mostrar las cotizaciones recibidas de cada aseguradora con prima, coberturas, deducibles, exclusiones, vigencia y fecha de expiración de la oferta, en un formato comparable.

#### Scenario: Respuestas parciales
- **WHEN** una aseguradora no responde dentro del tiempo configurado
- **THEN** el sistema muestra las cotizaciones recibidas e indica cuáles siguen pendientes

### Requirement: Datos del peritaje para la cotización
El sistema DEBE (SHALL) compartir con NEXUS el informe de inspección y la ficha registral solo con el consentimiento expreso del comprador o propietario.

#### Scenario: Sin consentimiento
- **WHEN** el usuario no acepta compartir el informe
- **THEN** la cotización se pide solo con los datos mínimos y se indica que puede requerir inspección de la aseguradora

### Requirement: Emisión al cerrar la venta
El sistema DEBE (SHALL) permitir aceptar una cotización y coordinar con NEXUS la emisión de la póliza con inicio de vigencia en la fecha del traspaso.

#### Scenario: Traspaso inscrito con póliza aceptada
- **WHEN** se inscribe el traspaso y el comprador aceptó una cotización
- **THEN** el sistema notifica a NEXUS para emitir y guarda la referencia de la póliza

### Requirement: Comisiones configurables
El sistema DEBE (SHALL) registrar las comisiones por póliza según el contrato con NEXUS, sin montos ni porcentajes en el código.

#### Scenario: Cambio de tarifa
- **WHEN** el administrador actualiza el porcentaje de comisión
- **THEN** aplica a las pólizas nuevas y queda auditado
