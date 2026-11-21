# Definir la plataforma Motoria.ai (base y roadmap)

Implementa la fase **F0** del roadmap (design D6).

## Why

En Costa Rica comprar o vender un carro usado es un proceso fragmentado y de baja confianza. Crautos solo publica anuncios: el comprador no sabe si los datos, el kilometraje o los gravámenes son reales, ni cuánto le costará asegurarlo o financiarlo, y el vendedor coordina a mano notario, Registro Nacional, marchamo y RTV. Motoria.ai quiere ser la referencia de confianza sin comprar inventario: un marketplace que acompaña la transacción de la placa al traspaso inscrito y vende el dato verificado del vehículo a bancos y aseguradoras. El backend ya tiene esqueleto de módulos y outbox, pero no especificación funcional. Este change define capacidades, reglas y orden de construcción; no escribe código. La comparación con Crautos y Kavak está en design D1.

## What Changes

Se crean las especificaciones base de nueve capacidades:

| Capacidad | Qué resuelve | Módulo backend |
|---|---|---|
| `vehiculo-registro` | Ficha del vehículo por placa: características, propietario, gravámenes y anotaciones. Consulta automática al Registro Nacional por verificar (design D7.1); mientras tanto, vía documento | `vehicle` |
| `publicacion-anuncios` | Captura guiada de datos y fotos, asistente de IA para el anuncio, ciclo de vida del anuncio | `listing`, `media` |
| `inspeccion-ia` | Peritaje por IA tipo inspección de seguro: daños, kilometraje, consistencia, nivel de certificación. Kilometraje de la RTV por verificar (design D7.3) | `inspection`, `certification`, `ai` |
| `red-aliados` | Autolavados y talleres afiliados que toman fotos certificadas y hacen revisión física | `partner` |
| `comparador-ia` | Comparativo con vehículos del mismo rango, precio justo y recomendación explicada para el comprador | `ai` |
| `cotizacion-seguros` | Cotización con varias aseguradoras a través de NEXUS Broker. API y comisión por verificar (design D7.5) | `insurance` (nuevo) |
| `traspaso-notarial` | Traspaso digital con notario aliado y firma digital BCCR. Presentación electrónica al Registro Nacional por verificar (design D7.2); si no existe, flujo híbrido | `notary` (nuevo) |
| `precalificacion-credito` | Expediente de precalificación del comprador y del vehículo para bancos | `financing` |
| `identidad-cumplimiento` | Identidad verificada, consentimientos Ley 8968, LC/FT, auditoría y trazabilidad de la IA. Obligación ante SUGEF por verificar (design D7.4) | `user`, `common` |

También se definen el modelo de negocio, las fuentes de datos con su riesgo, la arquitectura de integraciones y el roadmap por fases (design.md).

## Decisiones del dueño (2026-11-21)

- Mercado inicial **Costa Rica**; modelo de datos multi-país desde el inicio para México en una fase posterior.
- **Marketplace con transacción**: no se compra inventario. Los ingresos vienen de publicación, certificación, traspaso, comisiones de seguro y crédito, y APIs de datos.
- **NEXUS Broker** es la corredora licenciada SUGESE que intermedia los seguros. Motoria no intermedia seguros por cuenta propia. El modelo de integración y de comisión está por verificar (design D7.5).
- OpenSpec en una **base** (este change) más un **change por fase**.

## Límites (requisitos, no sugerencias)

- Motoria **no da fe pública**: el traspaso lo autoriza un notario habilitado. Motoria orquesta, prepara y custodia.
- Motoria **no aprueba ni formaliza créditos ni consulta el CIC de SUGEF**: entrega un expediente de precalificación; el banco decide.
- Motoria **no cotiza ni vende seguros directamente**: la cotización y la póliza las emite NEXUS Broker con las aseguradoras.
- Motoria **no custodia por sí misma el precio de venta**: el pago protegido lo presta una entidad financiera aliada (por verificar, design D7.7).
- **La IA sugiere y el humano decide**: un resultado de IA nunca rechaza un anuncio ni certifica un vehículo sin un umbral configurado o una revisión humana.
- **No se evaden controles de terceros**: si una fuente (Registro Nacional, RTV, aseguradoras) no ofrece acceso autorizado, se usa una vía manual o un convenio; no se resuelven captchas ni se imitan navegadores.

## Impact

- Nuevas specs en `openspec/specs/` al archivar.
- Nuevos módulos backend: `insurance` y `notary`. Los demás ya existen como esqueleto. Nuevo estado `RESERVED` en `ListingStatus` y eventos nuevos (design D4).
- Dependencias externas que exigen convenio o contrato (puertas del dueño, tasks sección 2): Registro Nacional, Dekra, marchamo y COSEVI, notarios aliados, entidad financiera de custodia, NEXUS Broker, bancos, red de aliados, proveedor de verificación de identidad y fuente de listas restrictivas.
- Afecta `MOTORIA_MASTER_PROMPT.md`: deja de ser la única fuente de alcance; manda el OpenSpec.
