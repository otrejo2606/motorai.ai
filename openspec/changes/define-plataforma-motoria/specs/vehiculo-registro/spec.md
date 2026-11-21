## ADDED Requirements

### Requirement: Ficha del vehículo por placa
El sistema DEBE (SHALL) crear o actualizar la ficha de un vehículo a partir de su placa y código de país, con los datos del registro oficial: marca, modelo, estilo, año, color, número de chasis o VIN, número de motor, cilindrada, combustible, tracción, número de puertas, capacidad, propietario registral, gravámenes y anotaciones.

#### Scenario: Consulta por placa con fuente disponible
- **WHEN** un vendedor ingresa una placa válida de Costa Rica
- **THEN** el sistema publica `vehicle.registry.requested`, consulta la fuente activa y guarda cada dato con su fuente, vía, fecha de consulta y evidencia

#### Scenario: Placa con formato inválido
- **WHEN** la placa no cumple el formato configurado para el país
- **THEN** el sistema rechaza la solicitud con un error de validación y no consulta ninguna fuente

### Requirement: Fuentes intercambiables y sin evasión
El sistema DEBE (SHALL) obtener los datos registrales mediante un puerto con adaptadores intercambiables por configuración (convenio oficial o documento aportado) y NO DEBE (MUST NOT) automatizar consultas públicas protegidas por captcha ni imitar navegadores para evadir controles.

#### Scenario: Sin convenio activo
- **WHEN** no hay adaptador de convenio activo para el país
- **THEN** el sistema pide al vendedor la certificación o consulta registral en PDF, la lee con IA y marca los datos como "verificado por documento" pendientes de validación

### Requirement: Dato verificado frente a dato declarado
El sistema DEBE (SHALL) distinguir en la ficha y en el anuncio cada dato verificado por fuente oficial de cada dato declarado por el vendedor, y mostrar esa distinción al comprador.

#### Scenario: Diferencia entre lo declarado y el registro
- **WHEN** el vendedor declara un año, color o cilindrada distinto del registrado
- **THEN** el sistema conserva el dato registral, registra la discrepancia y la envía a revisión antes de publicar

### Requirement: Gravámenes y anotaciones bloqueantes
El sistema DEBE (SHALL) marcar el vehículo como no apto para traspaso cuando el registro muestre gravámenes, embargos, anotaciones o colisiones sin resolver, y mostrar el motivo al vendedor y al comprador.

#### Scenario: Vehículo con prenda vigente
- **WHEN** la ficha registral muestra una prenda vigente
- **THEN** el anuncio puede publicarse con la advertencia visible, pero el traspaso no puede iniciarse hasta que se registre la cancelación o el acreedor participe

### Requirement: Titularidad del vendedor
El sistema DEBE (SHALL) verificar que la identidad del vendedor coincide con el propietario registral, o exigir un poder o autorización válidos, antes de publicar un anuncio certificado.

#### Scenario: Vendedor no propietario
- **WHEN** la cédula verificada del vendedor no coincide con el propietario registral
- **THEN** el sistema pide el poder o la autorización y no publica hasta que un analista la valide

### Requirement: Vigencia de los datos registrales
El sistema DEBE (SHALL) guardar la fecha de cada consulta y volver a consultar la fuente antes de iniciar un traspaso cuando la última consulta supere la antigüedad máxima configurada.

#### Scenario: Consulta antigua al iniciar traspaso
- **WHEN** se inicia un traspaso y la última consulta registral supera la antigüedad configurada
- **THEN** el sistema renueva la consulta y detiene el traspaso si aparece un gravamen nuevo
