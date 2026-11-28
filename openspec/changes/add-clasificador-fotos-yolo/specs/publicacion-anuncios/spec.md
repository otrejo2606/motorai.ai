## ADDED Requirements

### Requirement: Servicio de inferencia del ángulo con el contrato del backend
El sistema DEBE (SHALL) ofrecer un servicio de inferencia propio que reciba `POST /v1/classify` con `expectedSlot`, `mimeType` e `imageBase64` y responda `modelVersion`, `predictions` (toma y confianza, de mayor a menor, hasta `TOP_K`), `vin` y `odometerKm`, con los códigos de error que espera `HttpPhotoAngleClassifierAdapter`. NO DEBE (MUST NOT) devolver etiquetas distintas de las 14 tomas del protocolo y `OTHER`, ni confianzas fuera de 0 a 1.

#### Scenario: Clasificación correcta
- **WHEN** el backend envía un JPEG válido de la toma `FRONT` y el modelo predice `FRONT` con 0.93 y `FRONT_LEFT` con 0.05
- **THEN** el servicio responde 200 con `modelVersion`, `predictions` `[{"slot": "FRONT", "confidence": 0.93}, {"slot": "FRONT_LEFT", "confidence": 0.05}]`, `vin` nulo y `odometerKm` nulo

#### Scenario: Etiquetas desconocidas del modelo
- **WHEN** el modelo devuelve una etiqueta que no es una de las 15 conocidas o una confianza fuera de 0 a 1
- **THEN** esa predicción no aparece en la respuesta; si no queda ninguna, el servicio responde 500 y el backend deja la foto `UNAVAILABLE`

#### Scenario: Sin modelo cargado
- **WHEN** falta `MODEL_PATH`, el archivo no existe o el modelo no se pudo cargar
- **THEN** el servicio arranca igual, `GET /health` responde 503 con `modelLoaded` falso y `POST /v1/classify` responde 503

#### Scenario: Modelo cargado
- **WHEN** el modelo se cargó al arranque
- **THEN** `GET /health` responde 200 con `modelLoaded` verdadero y la versión del modelo

#### Scenario: Sin OCR
- **WHEN** no hay un extractor de lecturas configurado
- **THEN** `vin` y `odometerKm` van nulos y la clasificación no cambia

### Requirement: Entrada segura del servicio de inferencia
El sistema DEBE (SHALL) aceptar solo imágenes JPEG, PNG o WebP del tipo declarado, con un límite de tamaño del cuerpo y de píxeles, y exigir el token Bearer cuando `CLASSIFIER_TOKEN` está configurado, comparándolo en tiempo constante. NO DEBE (MUST NOT) decodificar los píxeles de una imagen que supera el límite.

#### Scenario: Imagen ilegible
- **WHEN** el base64 es inválido, la imagen está vacía, dañada o truncada
- **THEN** el servicio responde 400 y el backend registra `PHOTO_CLASSIFIER_IMAGE_UNREADABLE`

#### Scenario: Tipo no admitido
- **WHEN** `mimeType` no es `image/jpeg`, `image/png` ni `image/webp`, o los bytes no son del tipo declarado
- **THEN** el servicio responde 415 sin llamar al modelo

#### Scenario: Demasiados píxeles
- **WHEN** el ancho por alto de la imagen supera `MAX_IMAGE_PIXELS`
- **THEN** el servicio responde 422 sin decodificar la imagen

#### Scenario: Cuerpo demasiado grande
- **WHEN** el cuerpo supera `MAX_BODY_BYTES`, con o sin `Content-Length`
- **THEN** el servicio responde 413 sin leer el resto ni llamar al modelo

#### Scenario: Token configurado
- **WHEN** `CLASSIFIER_TOKEN` está configurado y la solicitud no trae `Authorization: Bearer` con ese valor
- **THEN** el servicio responde 401 antes de leer el cuerpo y el backend registra `PHOTO_CLASSIFIER_NOT_CONFIGURED`

### Requirement: Privacidad del servicio de inferencia
El sistema NO DEBE (MUST NOT) guardar en disco ni registrar en los logs la imagen, su base64 ni la toma enviada por el cliente, ni enviar telemetría de Ultralytics. El servicio DEBE (MUST) escuchar solo en la red privada (túnel WireGuard) y correr como usuario no root.

#### Scenario: Falla de la inferencia
- **WHEN** el modelo lanza una excepción al predecir
- **THEN** el servicio responde 500 con el código `INFERENCE_FAILED`, registra solo el tipo de error y no incluye detalles ni datos de la imagen en la respuesta

#### Scenario: Despliegue en la VM de IA
- **WHEN** se levanta el servicio con el compose de `ml/photo-classifier/deploy`
- **THEN** el puerto se publica solo en `WG_BIND_ADDRESS`, el modelo se monta de solo lectura y el contenedor corre sin privilegios y con `YOLO_OFFLINE=true`

### Requirement: Dataset con base legal y separado por vehículo
El sistema DEBE (SHALL) armar el dataset de entrenamiento solo con fotos de vendedores con el consentimiento `MODEL_TRAINING` vigente, fotos propias o de aliados con licencia. DEBE (MUST) dividirlo de forma estratificada por toma y agrupada por vehículo con semilla fija, y normalizar cada foto como el backend (rotación por EXIF, lado mayor de 1024 y JPEG sin metadatos). NO DEBE (MUST NOT) entrenar con volteo horizontal.

#### Scenario: Mismo vehículo en una sola división
- **WHEN** `prepare_dataset` recibe varias fotos con el mismo `vehicle_id`
- **THEN** todas quedan en la misma división (train, val o test)

#### Scenario: Conjunto dorado fijado
- **WHEN** una fila del CSV trae `split` igual a `test`
- **THEN** el vehículo de esa fila queda en test

#### Scenario: Ruta fuera de la raíz
- **WHEN** una fila del CSV apunta fuera de `--images-root`
- **THEN** la fila se descarta y se cuenta en `summary.json`

#### Scenario: Clase faltante
- **WHEN** a una división le falta alguna de las 15 etiquetas
- **THEN** el script termina con error y no se entrena con clases desalineadas

### Requirement: Métricas mínimas antes de conectar el modelo
El sistema DEBE (SHALL) evaluar el modelo con la misma regla de umbrales de `PhotoAnglePolicy` y no conectar el proveedor `http` en producción hasta cumplir los mínimos que confirme el dueño en la puerta 0.2; la propuesta es:

- exactitud top-1 de al menos 95 % en exteriores y 90 % en detalle;
- falsos `MISMATCH` por debajo de 1 % con los umbrales del backend;
- latencia p95 menor que 300 ms en la VM de IA.

#### Scenario: Reporte de evaluación
- **WHEN** se corre `evaluate` con el conjunto de prueba y los umbrales 0.80 y 0.90
- **THEN** el reporte trae la exactitud por grupo, la matriz de confusión, los conteos `MATCH`, `MISMATCH` y `UNCERTAIN` del vendedor honesto y de la foto equivocada, el barrido de umbrales, la latencia p95 y `meetsMinimums`

### Requirement: Licencia de Ultralytics
El sistema DEBE (SHALL) mantener el código del servicio disponible, bajo AGPL-3.0, para quien interactúe con él por red mientras use Ultralytics con la licencia gratuita. NO DEBE (MUST NOT) atender fotos de usuarios reales en el lanzamiento sin la licencia Enterprise de Ultralytics firmada, ni distribuir el modelo al navegador antes de esa firma.

#### Scenario: Desarrollo con AGPL-3.0
- **WHEN** el servicio corre en desarrollo con Ultralytics
- **THEN** `ml/photo-classifier/LICENSE-NOTICE.md` declara la AGPL-3.0 y el backend y el portal solo hablan HTTP con el servicio, sin incluir código de Ultralytics

#### Scenario: Lanzamiento
- **WHEN** se prepara el lanzamiento de la validación del ángulo con el modelo propio
- **THEN** la puerta 0.4 exige la licencia Enterprise firmada o el cambio a un clasificador con licencia permisiva detrás del mismo contrato
