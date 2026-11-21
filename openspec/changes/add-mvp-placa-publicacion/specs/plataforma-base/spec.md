## ADDED Requirements

### Requirement: Migraciones versionadas
El sistema DEBE (SHALL) crear y cambiar su esquema de base de datos solo mediante migraciones Flyway versionadas, y NO DEBE (MUST NOT) generar ni actualizar el esquema desde las entidades en ningún perfil desplegado.

#### Scenario: Arranque sobre una base existente
- **WHEN** la aplicación arranca contra una base creada antes con generación automática
- **THEN** Flyway toma la versión 1 como línea base sin borrar datos y aplica las migraciones siguientes

### Requirement: Columnas de auditoría
El sistema DEBE (SHALL) incluir en toda tabla nueva las columnas `created_at`, `created_by`, `updated_at` y `updated_by`, llenadas por la aplicación.

#### Scenario: Registro creado por un vendedor
- **WHEN** un vendedor crea un anuncio
- **THEN** la fila guarda el sujeto del vendedor en `created_by` y la hora en `created_at`

### Requirement: Textos en español e inglés
El sistema DEBE (SHALL) mostrar todo texto para el usuario desde archivos de traducción en español e inglés con las mismas claves, y los mensajes de error del backend DEBEN (MUST) devolver un código estable que el frontend traduce.

#### Scenario: Cambio de idioma
- **WHEN** el usuario cambia el idioma a inglés
- **THEN** todas las pantallas del flujo de venta se muestran en inglés sin recargar

### Requirement: Carga segura de archivos
El sistema DEBE (SHALL) aceptar archivos solo por URL prefirmada emitida por el backend, con tipo verificado por su firma de contenido, tamaño máximo configurable, hash sha256 y clave generada en el servidor.

#### Scenario: Archivo con extensión falsa
- **WHEN** se sube un ejecutable renombrado como `.pdf`
- **THEN** el backend rechaza el archivo al confirmarlo y borra el objeto

#### Scenario: Foto pública
- **WHEN** se publica una foto de un anuncio
- **THEN** la copia pública no contiene metadatos EXIF y el original queda privado
