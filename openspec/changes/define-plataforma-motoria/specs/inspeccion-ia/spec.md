## ADDED Requirements

### Requirement: Peritaje por IA tipo inspección de seguro
El sistema DEBE (SHALL) analizar las fotos del protocolo y producir un informe de peritaje por panel y componente con: tipo de daño (rayón, abolladura, quebradura, corrosión, pintura no original, desalineación de piezas), severidad, ubicación marcada sobre la foto, confianza y costo de reparación estimado dentro de un rango.

#### Scenario: Daño detectado
- **WHEN** la IA detecta una abolladura en la puerta delantera izquierda
- **THEN** el informe incluye el panel, la severidad, la foto marcada, la confianza y el rango de costo, y publica `inspection.ai.completed`

### Requirement: Verificación del kilometraje
El sistema DEBE (SHALL) leer el kilometraje de la foto del odómetro, compararlo con el declarado, con las lecturas anteriores de la misma placa en Motoria, con el registrado en la RTV cuando exista esa fuente y con el desgaste visible (volante, pedales, asiento), y señalar inconsistencias.

#### Scenario: Kilometraje menor que en una publicación anterior
- **WHEN** el odómetro muestra menos kilómetros que una lectura anterior de la misma placa en Motoria
- **THEN** el sistema marca alerta alta de alteración de odómetro y envía el caso a revisión humana

#### Scenario: Kilometraje menor que en la última RTV
- **WHEN** el odómetro muestra menos kilómetros que la última RTV conocida
- **THEN** el sistema marca alerta alta de alteración de odómetro y envía el caso a revisión humana

### Requirement: Consistencia con el registro
El sistema DEBE (SHALL) verificar que el VIN o número de chasis, el color, el estilo y la placa visibles en las fotos coinciden con la ficha registral.

#### Scenario: VIN distinto
- **WHEN** el VIN leído en la foto no coincide con el del registro
- **THEN** el sistema bloquea la certificación y registra la señal de fraude

### Requirement: Integridad de las fotos
El sistema DEBE (SHALL) guardar cada foto con hash sha256, metadatos de captura (fecha, dispositivo y ubicación cuando haya consentimiento) y un análisis de manipulación, y DEBE (MUST) rechazar fotos editadas o reutilizadas de otros anuncios.

#### Scenario: Foto reutilizada
- **WHEN** una foto coincide por huella perceptual con la de otro anuncio
- **THEN** el sistema la rechaza y notifica al analista

### Requirement: Niveles de certificación con decisión humana
El sistema DEBE (SHALL) asignar un nivel de certificación según umbrales configurables y DEBE (MUST) enviar a un analista todo caso bajo el umbral de confianza, con alertas altas o con daño estructural; ningún vehículo pasa de nivel solo por IA si la regla exige revisión.

#### Scenario: Caso dudoso
- **WHEN** la confianza global del peritaje está bajo el umbral configurado
- **THEN** el sistema publica `inspection.review.required` y el nivel queda pendiente hasta la decisión del analista

### Requirement: Informe explicable y reutilizable
El sistema DEBE (SHALL) emitir un informe de inspección legible para el comprador, exportable en PDF y consumible por API por aseguradoras y bancos autorizados, con la versión del modelo, la fecha y la vigencia.

#### Scenario: Aseguradora consulta el informe
- **WHEN** NEXUS Broker pide el informe de un vehículo con consentimiento del propietario
- **THEN** el sistema entrega el informe vigente y registra el acceso en la auditoría
