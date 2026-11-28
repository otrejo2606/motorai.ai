# Design: plataforma Motoria.ai

## D1. Posicionamiento

| | Crautos | Kavak | Motoria.ai |
|---|---|---|---|
| Modelo | Clasificados | Compra y revende inventario | Marketplace con transacción completa |
| Datos del vehículo | Los escribe el vendedor | Inspección propia | Registro Nacional por placa más inspección IA y aliados |
| Confianza | Baja | Alta (garantía propia) | Alta: nivel de certificación verificable |
| Traspaso | Por cuenta de las partes | Incluido | Notaría digital integrada |
| Seguro y crédito | No | Crédito propio | Varias aseguradoras (NEXUS) y varios bancos |
| Capital requerido | Bajo | Muy alto | Bajo a medio |

La ventaja defendible no es el listado, que es fácil de copiar, sino **el dato verificado del vehículo** (registro, inspección, historial, precio de mercado) y **el cierre de la transacción**. Ese dato se monetiza tres veces: al comprador (confianza), al banco (riesgo de garantía) y a la aseguradora (peritaje previo).

## D2. Modelo de ingresos (configurable, sin montos en código)

1. Publicación: gratuita básica; planes destacados y para agencias (módulo `subscription`).
2. Certificación Motoria: cobro por inspección IA más sesión en un aliado (reparto de ingreso con el aliado).
3. Traspaso digital: tarifa de servicio aparte de honorarios notariales, impuesto de traspaso y timbres, que se trasladan transparentes.
4. Seguros: comisión compartida con NEXUS Broker según su contrato.
5. Crédito: comisión por expediente originado o por crédito formalizado, según convenio con cada banco.
6. API de datos (expuesta por Apache APISIX, D4): valuación, historial e informe de inspección para bancos, aseguradoras y agencias.
7. Promoción en redes (módulo `promotion`).

## D3. Fuentes de datos y riesgo (todo "por verificar" hasta tener documento)

| Fuente | Dato | Vía preferida | Vía de respaldo | Riesgo |
|---|---|---|---|---|
| Registro Nacional (Bienes Muebles) | Características, propietario, gravámenes, anotaciones, infracciones registradas | Convenio o servicio web autorizado del RN | Informe o certificación registral comprada en rnpdigital.com por el vendedor o por un operador de Motoria y leída con IA | Alto: los convenios conocidos de 2026 son con instituciones públicas y BCR (D8); no se halló API comercial. **No se automatiza la consulta pública con captcha.** |
| Marchamo (INS / Hacienda) | Derechos de circulación al día (requisito del traspaso) | Convenio | Comprobante subido por el vendedor | Medio |
| RTV (Dekra, permiso temporal) | Vigencia y, si existe, kilometraje en cada revisión | Convenio | Comprobante subido | Alto: el operador está en disputa (D8) y no se confirmó que registre ni comparta el kilometraje. El antifraude de odómetro se apoya primero en el historial propio de Motoria |
| COSEVI | Multas pendientes | Convenio | Declaración y comprobante | Medio |
| Valor fiscal (Hacienda) | Base del impuesto de traspaso: el mayor entre el valor del contrato y el fiscal (D8); la tasa vigente está por verificar | Tabla publicada o convenio | Cálculo manual por el notario | Bajo |
| Catálogo técnico (VIN) | Versión, equipo, motor | Decodificador VIN comercial | Captura guiada | Bajo |
| Precios de mercado | Rango del mismo modelo | Datos propios de anuncios y transacciones | Fuentes públicas con permiso | Arranque en frío: pocos datos al inicio |

Regla: cada dato guarda **fuente, vía, fecha de consulta y evidencia** (respuesta o documento). La ficha muestra al comprador qué está verificado y qué es declarado.

## D4. Arquitectura

- Monolito modular Quarkus (el existente). Cada integración externa vive en `{modulo}/integration` detrás de un puerto (interfaz) con adaptadores intercambiables: `RegistroNacionalPort` con `RegistroConvenioAdapter` y `RegistroDocumentoAdapter` (el documento lo aporta el vendedor o un operador que compra el informe en rnpdigital.com). El adaptador activo se elige por configuración y por país.
- Asíncrono por RabbitMQ con outbox (ya existe). Eventos nuevos: `vehicle.registry.requested`, `vehicle.registry.review.required`, `vehicle.registry.completed`, `inspection.photos.submitted`, `inspection.ai.completed`, `inspection.review.required`, `insurance.quote.requested`, `insurance.quote.received`, `notary.transfer.requested`, `notary.deed.signed`, `notary.registry.inscribed`, `credit.prequal.requested`, `credit.prequal.delivered`. Todos con id de correlación, reintento y DLQ.
- Relación con los eventos existentes de `common/event/EventType`: se conservan `listing.*`, `inspection.scheduled`, `media.uploaded`, `ai.price.recommended`, `finance.simulation.*`, `promotion.requested` y `notification.send`. `inspection.ai.completed` es el resultado del peritaje y lo consume `inspection`; el actual `ai.photo.analysis.completed` queda como evento por foto. `inspection.completed` se mantiene como cierre de la inspección completa (IA más revisión humana o del aliado) y dispara `listing.certification.completed`. `listing.sold` se publica al recibir `notary.registry.inscribed`. `vehicle.registry.*`, `insurance.*`, `notary.*` y `credit.*` son nuevos y no duplican ninguno.
- `ListingStatus` agrega el estado nuevo `RESERVED` (anuncio con traspaso en curso), entre `PUBLISHED` y `SOLD`; si el traspaso se cancela, vuelve a `PUBLISHED`.
- Convención: los estados y códigos de enumeración se escriben en inglés (como en el código existente); los textos para el usuario, en español e inglés por i18n.
- IA en el módulo `ai` con un registro de inferencias (`ai_inference`): modelo y versión, prompt o configuración, entradas por referencia, salida, confianza, explicación y revisor humano. Visión para daños y odómetro; LLM para el asistente, la lectura de documentos y la explicación del comparador.
- Archivos (fotos, documentos, escrituras) en S3 con hash sha256, metadatos de captura y retención por tipo.
- Identidad y gateway, todo open source (decisión del dueño del 2026-11-21: se descarta WSO2 IS y WSO2 APIM por peso):
  - **Keycloak** (Apache 2.0) como proveedor OIDC: realm `motoria`, flujo authorization code con PKCE para los portales (clientes públicos `motoria-portal-cliente` y `motoria-backoffice`), cliente confidencial `motoria-backend`. Los roles son roles de realm en el claim `realm_access.roles`; Quarkus los lee con `quarkus.oidc.roles.role-claim-path=realm_access/roles`. El id de usuario es el claim `sub`. Más adelante: federación con firma digital BCCR o con el proveedor de identidad elegido como proveedor de identidad externo de Keycloak.
  - **Apache APISIX** (Apache 2.0) como API gateway: TLS, enrutamiento, límite de peticiones, plugin `openid-connect` para validar el JWT de Keycloak en el borde, reescritura de `X-Forwarded-For` (lo pide `RateLimitingFilter`) y métricas Prometheus. Para la API de datos de F8: consumidores con `key-auth` o `jwt-auth`, cuotas con `limit-count` y registro de uso para facturar. El portal de desarrolladores queda para F8: un portal propio sobre los consumidores de APISIX o Gravitee APIM CE si hace falta uno completo.
  - Alternativa más liviana evaluada: KrakenD CE (sin estado, configuración por archivo), descartada porque la gestión de consumidores y cuotas que necesita F8 es de su edición empresarial.
  - El backend sigue validando el token por sí mismo (`quarkus-oidc`) y aplica `@RolesAllowed`; el gateway es una defensa adicional, no la única.
- Roles: `BUYER`, `SELLER`, `BACKOFFICE` y `ADMIN` (ya usados en el código; `BACKOFFICE` cubre a los analistas de revisión y cumplimiento) más los nuevos `DEALER` (agencia), `PARTNER` (aliado), `NOTARY`, `BANK` e `INSURANCE_BROKER` (NEXUS).
- Portales: cliente (comprar y vender), backoffice (analistas, revisión de IA, cumplimiento), admin (configuración) y **portal de aliados** (agenda y captura), más el portal del notario y el del banco como vistas restringidas del backoffice.

## D5. Flujo de punta a punta

1. El vendedor escribe la placa, se verifica su identidad y se confirma que es el propietario según el Registro (o que tiene poder).
2. Motoria arma la ficha con los datos del Registro; el vendedor completa lo que falta con el asistente de IA.
3. El vendedor toma las fotos con la app (protocolo guiado) o agenda una sesión en un aliado.
4. La IA hace el peritaje (daños, kilometraje, consistencia con el Registro y la RTV) y asigna un nivel de certificación; los casos dudosos van a un analista.
5. Se publica el anuncio con su nivel, el precio sugerido y la posición en el rango del mercado.
6. El comprador compara, cotiza el seguro (NEXUS) y pide la precalificación de crédito.
7. Las partes acuerdan la venta y se abre el traspaso digital: el notario aliado revisa, se firma con firma digital BCCR, se pagan impuestos y timbres, y se presenta al Registro.
8. Al inscribirse, el anuncio pasa a `SOLD`, NEXUS emite la póliza y se notifica al banco para que formalice el crédito.

## D6. Roadmap por fases (un change por fase)

| Fase | Change | Contenido | Puertas del dueño |
|---|---|---|---|
| F0 | `define-plataforma-motoria` | Este change: specs y roadmap | Aprobar alcance |
| F1 MVP | `add-mvp-placa-publicacion` | Identidad y consentimientos, ficha por placa (vía documento), publicación con asistente IA, captura guiada de fotos, búsqueda | Proveedor de identidad; registro de la base ante PRODHAB |
| F2 | `add-inspeccion-ia-certificacion` | Peritaje IA, niveles de certificación, revisión humana, red de aliados (piloto con 3 a 5 aliados) | Contratos con aliados; conjunto dorado de fotos |
| F3 | `add-comparador-precio` | Comparador del mismo rango, precio justo y recomendación al comprador | Fuente inicial de precios |
| F4 | `add-seguros-nexus` | Cotización multiaseguradora vía NEXUS, emisión al cerrar la venta | Contrato y API de NEXUS |
| F5 | `add-traspaso-notarial-digital` | Expediente, borrador de escritura, firma digital, pagos, presentación y seguimiento | Notarios aliados; validación legal del flujo electrónico ante el RN y la DNN; entidad financiera de custodia del pago |
| F6 | `add-precalificacion-credito` | Expediente para bancos, portal del banco, API | Convenios con 1 a 2 bancos |
| F7 | `add-convenios-datos-oficiales` | Adaptadores por convenio: Registro Nacional, RTV, marchamo, COSEVI | Convenios firmados |
| F8 | `add-api-datos-y-mexico` | Monetización por API y adaptación a México (REPUVE, notarías, CNBV, CNSF) | Decisión de expansión |

F1 a F3 ya compiten con Crautos en confianza; F4 a F6 cierran la transacción, que es lo que Crautos no tiene.

## D7. Preguntas abiertas

1. ¿Ofrece el Registro Nacional un servicio web o convenio comercial para consultar vehículos por placa? ¿Con qué costo por consulta? (Abierta; ver D8.1.)
2. ¿Acepta hoy el Registro Nacional la presentación electrónica del testimonio de traspaso de vehículos firmado digitalmente? ¿Qué exige la Dirección Nacional de Notariado? (Parcialmente resuelta a favor; ver D8.2. Falta confirmar que el traspaso de vehículos no está entre las excepciones y los requisitos de la DNN.)
3. ¿Registra la RTV el kilometraje en cada revisión y lo comparte por convenio? (Abierta y de alto riesgo; ver D8.3.)
4. ¿Está la compraventa de vehículos usados sujeta a la Ley 7786 (artículo 15 bis) como actividad no financiera? Define si Motoria debe inscribirse ante SUGEF. (Abierta; hay reforma en trámite, ver D8.4. Requiere criterio de abogado.)
5. ¿Qué modelo de comisión y qué API ofrece NEXUS Broker para cotizar en línea?
6. ¿La garantía del vehículo en un crédito (prenda) se inscribe en el mismo acto del traspaso? Esto define si el notario de Motoria la redacta.
7. ¿Qué entidad financiera presta la custodia del precio de venta (cuenta de garantía o fideicomiso) y con qué requisitos LC/FT?
8. ¿Qué fuente de listas restrictivas se usa y quién es el oficial de cumplimiento? Depende de la respuesta 4.

## D8. Investigación en fuentes públicas (2026-11-21)

Búsqueda web de escritorio; nada se confirmó con el Registro Nacional, el MOPT ni un abogado. Sirve para orientar las puertas, no como criterio legal.

1. **Consulta por placa.** El RN firmó en 2026 convenios de Ventanilla Digital con Hacienda, el Poder Judicial y el COSEVI; el BCR emite certificaciones digitales del RN por convenio. No se halló un servicio web comercial para empresas privadas. Rnpdigital.com vende informes registrales y certificaciones de bienes muebles (gravámenes, historia de movimientos, motor). Consecuencia: F1 usa la vía documento (informe o certificación de rnpdigital leída con IA); el convenio sigue como puerta F7. Fuentes: [convenios Ventanilla Digital 2026](https://www.rnpdigital.com/noticias/comunicados/2026/05%20Convenios%20ventanilla%20digital.pdf), [BCR autorizado por el RN](https://rnpdigital.com/16%20BCR%20es%20la%20primera%20entidad%20autorizada%20por%20el%20RN.pdf), [Guía de Servicios Bienes Muebles 2025](https://rnpdigital.com/centroinforegistral/registros/bienes%20muebles/GuiasCalificacion/Guia-de-Servicios-Bienes-Muebles-2025.pdf).
2. **Traspaso digital.** La Ventanilla Digital del RN recibe documentos digitales autorizados por notarios con firma digital avanzada, también por "asistentes digitales" designados por el notario. Cubre Bienes Muebles (escrituras y protocolizaciones), con excepciones que se presentan en físico. El título de propiedad se entrega como adjunto descargable. El traspaso sigue exigiendo escritura pública, timbres, impuesto de transferencia y marchamo al día. Consecuencia: el traspaso 100 % digital es viable si las partes y el notario tienen firma digital; el flujo híbrido queda para quien no la tenga. Fuentes: [Ventanilla Digital, Bienes Muebles](https://rnpdigital.com/Presentacion%20Ventanilla%20Digital%20documentos%20de%20Bienes%20Muebles.pdf), [especificaciones técnicas de Ventanilla Digital](https://www.rnpdigital.com/aplicativo/Especificaciones_tecnicas_y_condiciones_plataforma_Ventanilla_Digital.pdf), [Guía de Servicios 2026](https://www.rnpdigital.com/centroinforegistral/registros/direccion_servicios/Guias/Guia-de-Servicios-Diarios-Archivos-y-Otros-2026.pdf), [requisitos de traspaso de vehículos exonerados](https://www.rnpdigital.com/Requisitos%20para%20el%20Traspaso%20de%20Vehiculos%20Exonerados.pdf).
3. **RTV.** Dekra opera desde 2022 con permiso en precario. El concurso para el nuevo operador (Applus y TÜV Rheinland preseleccionados) está trabado por un recurso de lesividad; en junio de 2026 se reportó que Dekra seguirá indefinidamente. Dekra ha dicho que el país no tiene un historial consolidado de vehículos inspeccionados. No se confirmó que se registre o comparta el kilometraje. Consecuencia: el convenio de RTV baja de prioridad; el antifraude de odómetro usa primero la lectura de fotos, el historial propio de cada placa en Motoria y el desgaste visible. Fuentes: [Dekra sigue indefinidamente](https://crhoy.com/nacionales/dekra-seguira-indefinidamente-en-revision-tecnica-sin-incorporar-nuevas-pruebas), [Dekra ofrece al MOPT su base de datos](https://www.diarioextra.com/noticia/dekra-ofrecera-al-mopt-base-de-datos-de-revision-tecnica/).
4. **Ley 7786.** No se obtuvo el texto vigente del artículo 15 bis. El expediente legislativo 25.129 (texto sustitutivo del 8 de abril de 2026) reescribe la lista de actividades obligadas del 15 bis. Consecuencia: la tarea 1.3 necesita un abogado que revise el texto vigente en el SCIJ y el estado del expediente. Fuentes: [texto sustitutivo del expediente 25.129](https://d1qqtien6gys07.cloudfront.net/wp-content/uploads/2026/05/Sust_25129_-08-abr.-2026.pdf), [ficha del expediente 25.129](https://delfino.cr/asamblea/proyecto/25129).
5. **Impuesto de traspaso.** La base es el mayor entre el valor del contrato y el valor fiscal; la tasa no se confirmó en esta búsqueda. La Ley 10.390 cambió la base del marchamo de los vehículos inscritos desde 2024. Consecuencia: las tasas y bases van en configuración, como ya exige la spec. Fuentes: [requisitos de traspaso, RN](https://www.rnpdigital.com/Requisitos%20para%20el%20Traspaso%20de%20Vehiculos%20Exonerados.pdf), [valor fiscal y Ley 10.390](https://delfino.cr/2024/11/marchamo-2025-hacienda-explica-por-que-dos-vehiculos-de-la-misma-marca-modelo-y-ano-pueden-tener-valor-fiscal-distinto).
