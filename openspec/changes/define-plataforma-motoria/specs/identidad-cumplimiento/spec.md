## ADDED Requirements

### Requirement: Identidad verificada por nivel
El sistema DEBE (SHALL) manejar niveles de identidad: correo y teléfono verificados para navegar y contactar; documento de identidad con prueba de vida para publicar, pedir crédito o traspasar; y firma digital certificada para firmar la escritura.

#### Scenario: Publicar sin identidad verificada
- **WHEN** un usuario con solo correo verificado intenta publicar
- **THEN** el sistema le pide verificar su documento de identidad

### Requirement: Consentimientos granulares
El sistema DEBE (SHALL) registrar consentimientos separados, versionados y revocables para el tratamiento de datos, el envío a NEXUS, el envío a cada banco, la ubicación de las fotos y el uso para entrenar modelos, según la Ley 8968.

#### Scenario: Revocación
- **WHEN** el usuario revoca el consentimiento de envío a un banco
- **THEN** el sistema deja de compartir datos nuevos con ese banco y registra la revocación

### Requirement: Derechos del titular
El sistema DEBE (SHALL) permitir al usuario consultar, rectificar, exportar y pedir la eliminación de sus datos, conservando solo lo que exija la ley (expedientes notariales, LC/FT) por el plazo configurado.

#### Scenario: Solicitud de eliminación con traspaso cerrado
- **WHEN** un usuario con un traspaso inscrito pide eliminar su cuenta
- **THEN** el sistema elimina o anonimiza lo eliminable y conserva el expediente notarial con acceso restringido

### Requirement: Prevención de fraude y LC/FT
El sistema DEBE (SHALL) evaluar señales de riesgo (identidad, VIN, fotos, odómetro, pagos inusuales) y verificar las partes contra listas restrictivas configuradas (fuente y oficial por definir en design D7.8), enviando los casos de riesgo a un oficial de cumplimiento.

#### Scenario: Coincidencia en lista restrictiva
- **WHEN** una parte coincide con una lista restrictiva
- **THEN** el traspaso se detiene y el caso pasa al oficial de cumplimiento sin avisar a la parte

### Requirement: Auditoría y trazabilidad de la IA
El sistema DEBE (SHALL) auditar cada acción sensible (acceso a expedientes, cambios de estado, decisiones humanas sobre resultados de IA) y registrar cada inferencia de IA con modelo, versión, entradas, salida, confianza, explicación y revisor.

#### Scenario: Analista corrige a la IA
- **WHEN** un analista cambia el nivel de certificación propuesto por la IA
- **THEN** el sistema registra ambos valores, el motivo y el analista, y el caso queda disponible para reentrenar

### Requirement: Seguridad de la IA conversacional
El sistema DEBE (SHALL) aislar los asistentes de IA de datos de otros usuarios, filtrar inyecciones de instrucciones en textos de anuncios y documentos, y limitar las herramientas que un asistente puede invocar a las del usuario autenticado.

#### Scenario: Instrucción oculta en una descripción
- **WHEN** la descripción de un anuncio contiene instrucciones para el asistente
- **THEN** el asistente las trata como datos y no cambia su comportamiento
