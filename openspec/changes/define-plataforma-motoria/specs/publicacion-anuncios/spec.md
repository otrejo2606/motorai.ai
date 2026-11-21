## ADDED Requirements

### Requirement: Captura guiada de datos del anuncio
El sistema DEBE (SHALL) prellenar el anuncio con la ficha registral y pedir al vendedor solo los datos que faltan: kilometraje, equipo, estado declarado, historial de mantenimiento, precio, ubicación y condiciones de negociación.

#### Scenario: Anuncio desde la placa
- **WHEN** el vendedor crea un anuncio de un vehículo con ficha registral
- **THEN** el formulario muestra los datos registrales como solo lectura y pide los campos restantes con validación por campo

### Requirement: Asistente de IA para el vendedor
El sistema DEBE (SHALL) ofrecer un asistente de IA que redacte el título y la descripción, sugiera el precio con su rango y su explicación, y detecte omisiones; el vendedor DEBE (MUST) aprobar el texto antes de publicarlo.

#### Scenario: Descripción sugerida
- **WHEN** el vendedor pide ayuda para la descripción
- **THEN** el sistema propone un texto basado solo en datos de la ficha y del vendedor, sin inventar equipo, y registra la inferencia en `ai_inference`

### Requirement: Protocolo de fotos
El sistema DEBE (SHALL) exigir un juego mínimo de fotos según un protocolo configurable (frente, trasera, laterales, cuatro esquinas, interior, tablero con el odómetro encendido, motor, llantas, VIN o número de chasis) y validar la calidad, el ángulo y el contenido de cada foto.

#### Scenario: Falta la foto del odómetro
- **WHEN** el vendedor intenta enviar sin la foto del tablero con el odómetro legible
- **THEN** el sistema indica qué falta y no permite solicitar certificación

#### Scenario: Foto de otro vehículo
- **WHEN** la IA detecta que una foto no corresponde al vehículo (color, modelo o placa distintos)
- **THEN** el sistema rechaza la foto y lo registra como señal de riesgo

### Requirement: Ciclo de vida del anuncio
El sistema DEBE (SHALL) gestionar los estados `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `RESERVED`, `SOLD`, `EXPIRED` y `REJECTED`, con transiciones válidas, auditoría de cada cambio y el evento correspondiente por outbox.

#### Scenario: Reserva al iniciar traspaso
- **WHEN** comprador y vendedor inician un traspaso
- **THEN** el anuncio pasa a `RESERVED` y deja de aparecer como disponible en la búsqueda

### Requirement: Nivel de confianza visible
El sistema DEBE (SHALL) mostrar en cada anuncio su nivel de confianza: datos registrales verificados, inspección IA, inspección en aliado y certificación Motoria, con la fecha de cada verificación.

#### Scenario: Anuncio sin inspección
- **WHEN** un comprador ve un anuncio que solo tiene datos registrales
- **THEN** el anuncio muestra "Datos registrales verificados" sin sello de inspección

### Requirement: Búsqueda y filtros
El sistema DEBE (SHALL) permitir buscar por marca, modelo, año, precio, kilometraje, provincia, combustible, transmisión y nivel de confianza, y ordenar por relevancia, precio, año o posición respecto al precio justo.

#### Scenario: Filtrar solo certificados
- **WHEN** el comprador filtra por "Certificado Motoria"
- **THEN** el resultado incluye solo anuncios con certificación vigente
