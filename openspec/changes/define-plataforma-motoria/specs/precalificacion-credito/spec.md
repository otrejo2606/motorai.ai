## ADDED Requirements

### Requirement: Simulación de crédito
El sistema DEBE (SHALL) simular la cuota mensual con plazo, prima, tasa de referencia por banco aliado y costos asociados, indicando que es una estimación no vinculante.

#### Scenario: Simulación en un anuncio
- **WHEN** el comprador ajusta la prima y el plazo
- **THEN** el sistema recalcula la cuota por banco y muestra la advertencia de estimación

### Requirement: Expediente de precalificación
El sistema DEBE (SHALL) armar, con el consentimiento expreso del comprador, un expediente con identidad verificada, ingresos y documentos declarados, el vehículo (ficha registral, peritaje, precio justo, relación préstamo-valor) y un resumen de riesgo explicado, y enviarlo solo a los bancos que el comprador elija.

#### Scenario: Comprador elige dos bancos
- **WHEN** el comprador autoriza el envío a dos bancos
- **THEN** el sistema publica `credit.prequal.requested` por banco y solo esos bancos acceden al expediente

### Requirement: El banco decide
El sistema NO DEBE (MUST NOT) aprobar ni rechazar créditos ni consultar el CIC de SUGEF; el resumen de riesgo DEBE (SHALL) presentarse como apoyo al análisis del banco, que registra su decisión.

#### Scenario: Banco responde
- **WHEN** el banco registra su decisión en su portal o por API
- **THEN** el sistema la muestra al comprador con las condiciones ofrecidas

### Requirement: Sin discriminación
El sistema NO DEBE (MUST NOT) usar en el resumen de riesgo variables protegidas (sexo, etnia, religión, nacionalidad, estado civil, discapacidad, edad salvo la legal) ni sustitutos evidentes de ellas.

#### Scenario: Revisión de variables del modelo
- **WHEN** se publica una nueva versión del modelo de riesgo
- **THEN** la versión registra sus variables y una revisión de sesgo aprobada antes de activarse

### Requirement: Valor de garantía para el banco
El sistema DEBE (SHALL) entregar al banco el valor de mercado estimado del vehículo, su confianza y el informe de inspección, para su análisis de la garantía.

#### Scenario: Valor bajo
- **WHEN** el préstamo solicitado supera la relación préstamo-valor configurada por el banco
- **THEN** el expediente lo indica al banco y al comprador sugiere una prima mayor
