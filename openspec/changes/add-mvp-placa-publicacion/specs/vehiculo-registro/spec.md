## ADDED Requirements

### Requirement: Lectura del informe registral con IA
El sistema DEBE (SHALL) extraer con IA, de un informe o certificación registral en PDF, los datos del vehículo, el propietario, los gravámenes, la placa y la fecha del documento, citando la página de cada valor, y DEBE (MUST) descartar todo valor sin cita.

#### Scenario: Informe legible
- **WHEN** el vendedor sube un informe registral legible de su placa
- **THEN** el sistema guarda cada campo con su confianza y página, registra la inferencia en `ai_inference` y publica `vehicle.registry.review.required`; `vehicle.registry.completed` se publica solo cuando el vehículo llega a `REGISTRY_VERIFIED`

#### Scenario: Placa distinta en el documento
- **WHEN** la placa del documento no coincide con la del vehículo
- **THEN** el sistema no aplica ningún dato y envía el caso a revisión

#### Scenario: Instrucciones dentro del PDF
- **WHEN** el texto del PDF contiene instrucciones dirigidas al modelo
- **THEN** el sistema las trata como datos y la salida sigue validándose contra el esquema

### Requirement: Revisión del informe por un analista
El sistema DEBE (SHALL) enviar a la bandeja del backoffice todo informe con algún campo obligatorio bajo el umbral de confianza, con antigüedad mayor a la configurada o con placa distinta, y mientras no se haya medido la precisión con el conjunto de prueba DEBE (MUST) enviar todos los informes.

#### Scenario: Analista corrige un campo
- **WHEN** el analista corrige el año extraído
- **THEN** el valor queda con fuente `ANALYST`, se registran el valor de la IA, el corregido y el analista, y el vehículo pasa a `REGISTRY_VERIFIED` al confirmar todos los campos

### Requirement: Informe obtenido por Motoria
El sistema DEBE (SHALL) permitir que el vendedor pida que un operador de Motoria obtenga el informe registral, con la tarifa configurada visible antes de aceptar.

#### Scenario: Solicitud del vendedor
- **WHEN** el vendedor elige "que Motoria lo obtenga" y acepta la tarifa
- **THEN** se crea una tarea en el backoffice y el vehículo queda en `REGISTRY_PENDING` hasta que el operador suba el informe
