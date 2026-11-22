## ADDED Requirements

### Requirement: Rango comparable
El sistema DEBE (SHALL) definir para cada vehículo un conjunto comparable (mismo segmento y rango de precio, año y kilometraje, con reglas configurables) y mostrar al comprador hasta N alternativas publicadas.

#### Scenario: Comparables de un sedán
- **WHEN** el comprador abre un anuncio de un sedán 2019
- **THEN** el sistema muestra sedanes comparables por precio, año y kilometraje, indicando el criterio usado

### Requirement: Precio justo explicado
El sistema DEBE (SHALL) estimar un rango de precio justo con un modelo trazable, indicar si el anuncio está bajo, dentro o sobre el rango, y explicar los factores principales (kilometraje, daños del peritaje, equipo, año, demanda).

#### Scenario: Pocos datos de mercado
- **WHEN** hay menos comparables que el mínimo configurado
- **THEN** el sistema muestra "estimación con baja confianza" o no muestra rango, y nunca presenta una cifra sin su confianza

### Requirement: Comparativo lado a lado
El sistema DEBE (SHALL) permitir comparar de 2 a 4 vehículos en ficha técnica, nivel de certificación, daños, costo estimado de seguro, consumo, costo de mantenimiento y cuota estimada de crédito.

#### Scenario: Comparar tres anuncios
- **WHEN** el comprador agrega tres anuncios al comparador
- **THEN** el sistema muestra la tabla con las diferencias resaltadas

### Requirement: Recomendación al comprador
El sistema DEBE (SHALL) ofrecer un asistente que, a partir del presupuesto, el uso y las preferencias del comprador, recomiende anuncios con sus razones a favor y en contra, sin favorecer anuncios pagados sin indicarlo.

#### Scenario: Anuncio destacado en la recomendación
- **WHEN** un anuncio pagado aparece entre las recomendaciones
- **THEN** se rotula como patrocinado y la explicación no lo presenta como el mejor por ese motivo

### Requirement: Trazabilidad de la recomendación
El sistema DEBE (SHALL) registrar cada recomendación y estimación de precio en `ai_inference` con las entradas, la versión del modelo y la explicación entregada.

#### Scenario: Auditoría de una recomendación
- **WHEN** un analista consulta por qué se recomendó un anuncio
- **THEN** el sistema muestra las entradas y la explicación tal como se entregaron
