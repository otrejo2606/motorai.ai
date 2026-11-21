# Tasks

Change de definición (F0): no lleva código. Marcas: (A) agente, (B) puerta del dueño, (C) calendario no acelerable. Una marca [x] significa "escrito", nunca "compilado ni probado". Las puertas siguen la fase del roadmap (design D6) y las preguntas abiertas (design D7).

## F0. Especificación base

- [x] 0.1 (A) `openspec/config.yaml` con contexto, normativa y reglas de redacción.
- [x] 0.2 (A) proposal.md con decisiones del dueño del 2026-11-21 y límites.
- [x] 0.3 (A) design.md: posicionamiento, ingresos, fuentes y riesgo, arquitectura, eventos, flujo, roadmap y preguntas abiertas.
- [x] 0.4 (A) Specs de las nueve capacidades: `vehiculo-registro`, `publicacion-anuncios`, `inspeccion-ia`, `red-aliados`, `comparador-ia`, `cotizacion-seguros`, `traspaso-notarial`, `precalificacion-credito`, `identidad-cumplimiento`.
- [ ] 0.5 (B) Revisar y aprobar el alcance y el roadmap.
- [ ] 0.6 (A) Actualizar `MOTORIA_MASTER_PROMPT.md` para remitir al OpenSpec como fuente de alcance y agregar los módulos `insurance` y `notary` (depende de 0.5).
- [ ] 0.7 (A) Archivar este change para mover las specs a `openspec/specs/` (depende de 0.5).
- [x] 0.8 (A) Investigación de las preguntas abiertas en fuentes públicas (design D8).

## F1. MVP placa y publicación (módulos `user`, `vehicle`, `listing`, `media`, `ai`)

- [ ] 1.1 (B) Elegir el proveedor de verificación de identidad con prueba de vida.
- [ ] 1.2 (B) Inscribir la base de datos ante PRODHAB (C: plazo de la autoridad).
- [ ] 1.3 (B) Criterio de abogado: si la compraventa de vehículos usados obliga a inscribirse ante SUGEF por la Ley 7786, artículo 15 bis, con el texto vigente y el estado del expediente 25.129 (D7.4, D8.4).
- [ ] 1.4 (B) Definir la fuente de listas restrictivas y nombrar al oficial de cumplimiento (D7.8; depende de 1.3).
- [x] 1.5 (A) Redactar el change `add-mvp-placa-publicacion` (proposal, design, tasks y specs). Su implementación sigue dependiendo de 0.5.

## F2. Inspección IA, certificación y aliados (módulos `inspection`, `certification`, `partner`, `ai`)

- [ ] 2.1 (B) Contratos con 3 a 5 aliados piloto.
- [ ] 2.2 (B) Conjunto dorado de fotos etiquetadas (daños, odómetro, VIN) para medir el peritaje (C: recolección).
- [ ] 2.3 (B) Consultar al operador de RTV vigente si registra y comparte por convenio el kilometraje de cada revisión (D7.3; prioridad baja por la disputa del operador, ver D8.3).

## F3. Comparador y precio justo (módulo `ai`)

- [ ] 3.1 (B) Fuente inicial de precios de mercado con permiso de uso, para el arranque en frío.

## F4. Seguros vía NEXUS (módulo `insurance`)

- [ ] 4.1 (B) Contrato y API de NEXUS Broker para cotización multiaseguradora y comisiones (D7.5).

## F5. Traspaso notarial digital (módulo `notary`)

- [ ] 5.1 (B) Validación legal del traspaso electrónico: confirmar que el traspaso de vehículos no está entre las excepciones de la Ventanilla Digital y los requisitos de la Dirección Nacional de Notariado (D7.2, D8.2).
- [ ] 5.2 (B) Convenios con notarios aliados.
- [ ] 5.3 (B) Entidad financiera para la custodia del precio de venta (D7.7; depende de 1.3).
- [ ] 5.4 (B) Definir si la prenda del crédito se inscribe en el mismo acto del traspaso (D7.6; depende de 6.1).

## F6. Precalificación de crédito (módulo `financing`)

- [ ] 6.1 (B) Convenios con 1 a 2 bancos.

## F7. Convenios de datos oficiales (módulos `vehicle`, `inspection`)

- [ ] 7.1 (B) Consultar al Registro Nacional si existe un servicio web o convenio para consultar vehículos por placa, con su costo (D7.1; mientras tanto, vía documento).
- [ ] 7.2 (B) Convenios de marchamo, RTV y COSEVI.

## F8. API de datos y México

- [ ] 8.1 (B) Decisión de expansión a México y modelo de precios de la API de datos.
