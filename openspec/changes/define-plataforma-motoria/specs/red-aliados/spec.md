## ADDED Requirements

### Requirement: Registro y habilitación de aliados
El sistema DEBE (SHALL) gestionar aliados (autolavados, talleres, centros de fotografía) con datos legales, ubicación, horarios, servicios ofrecidos, capacidad diaria y estado `PENDING`, `ACTIVE`, `SUSPENDED`; solo un aliado activo y capacitado en el protocolo DEBE (MUST) recibir citas.

#### Scenario: Aliado nuevo
- **WHEN** un taller completa su registro
- **THEN** queda `PENDING` hasta que un administrador verifique sus documentos y su capacitación

### Requirement: Agenda de sesiones
El sistema DEBE (SHALL) permitir al vendedor agendar una sesión de fotos o de revisión física en un aliado cercano según disponibilidad, con confirmación y recordatorios.

#### Scenario: Agendar sesión
- **WHEN** el vendedor elige un aliado y un horario libre
- **THEN** el sistema reserva el cupo, publica `inspection.scheduled` y envía la confirmación a ambos

### Requirement: Captura certificada en el aliado
El sistema DEBE (SHALL) dar al aliado una app de captura que siga el protocolo paso a paso, firme cada foto con la identidad del operador y la ubicación del aliado, y marque la inspección como "capturada en aliado".

#### Scenario: Captura fuera del local
- **WHEN** la ubicación de la captura no corresponde al aliado
- **THEN** el sistema marca la sesión con alerta y no le otorga el sello de aliado

### Requirement: Revisión física complementaria
El sistema DEBE (SHALL) permitir que un taller aliado registre una lista de verificación mecánica configurable (motor, transmisión, frenos, suspensión, escáner OBD) que se suma al informe de inspección.

#### Scenario: Escáner con códigos de falla
- **WHEN** el taller registra códigos OBD activos
- **THEN** el informe los muestra con su descripción y afectan el nivel de certificación según la regla configurada

### Requirement: Calidad y liquidación del aliado
El sistema DEBE (SHALL) medir la calidad de cada aliado (fotos rechazadas, tiempos, reclamos) y liquidar su participación del ingreso según la tarifa configurada.

#### Scenario: Aliado bajo el umbral de calidad
- **WHEN** el porcentaje de fotos rechazadas de un aliado supera el umbral en el periodo
- **THEN** el sistema avisa al administrador y puede suspender nuevas citas
