-- V8: consulta de personas por documento en el registro (Credid) para la verificacion de identidad y el backoffice.
-- F1 reservo V1 a V19 (design D2 de add-mvp-placa-publicacion); F2 usa V20 a V39.
-- La cedula nunca se guarda en claro: la consulta se indexa por el hash con llave (id_number_hash, igual que user_profile).
-- Se guarda solo lo minimo para verificar la identidad (nombre, tipo de persona, vencimiento del documento, nacionalidad).

CREATE TABLE usr.person_registry_check (
    id                  uuid         NOT NULL,
    id_number_hash      varchar(64)  NOT NULL,
    provider            varchar(40)  NOT NULL,
    found               boolean      NOT NULL,
    person_type         varchar(1),
    full_name           varchar(300),
    document_expires_on date,
    nationality         varchar(100),
    provider_reference  varchar(60),
    checked_at          timestamptz  NOT NULL,
    created_at          timestamptz  NOT NULL,
    created_by          varchar(255) NOT NULL,
    updated_at          timestamptz  NOT NULL,
    updated_by          varchar(255) NOT NULL,
    CONSTRAINT person_registry_check_pkey PRIMARY KEY (id),
    CONSTRAINT person_registry_check_person_type_ck CHECK (person_type IS NULL OR person_type IN ('F', 'J', 'E'))
);

-- Reuso de la consulta vigente por cedula y conteo del cupo diario de consultas pagadas.
CREATE INDEX ix_person_registry_check_hash ON usr.person_registry_check (id_number_hash, checked_at);
CREATE INDEX ix_person_registry_check_checked ON usr.person_registry_check (checked_at);

-- Bitacora de cada uso de una consulta (pagada o reutilizada): quien, para que y por que.
CREATE TABLE usr.person_registry_access (
    id              uuid         NOT NULL,
    check_id        uuid         NOT NULL,
    purpose         varchar(30)  NOT NULL,
    reason          varchar(300),
    verification_id uuid,
    created_at      timestamptz  NOT NULL,
    created_by      varchar(255) NOT NULL,
    updated_at      timestamptz  NOT NULL,
    updated_by      varchar(255) NOT NULL,
    CONSTRAINT person_registry_access_pkey PRIMARY KEY (id),
    CONSTRAINT person_registry_access_check_fk FOREIGN KEY (check_id) REFERENCES usr.person_registry_check (id),
    CONSTRAINT person_registry_access_verification_fk FOREIGN KEY (verification_id) REFERENCES usr.identity_verification (id),
    CONSTRAINT person_registry_access_purpose_ck CHECK (purpose IN ('IDENTITY_VERIFICATION', 'BACKOFFICE_LOOKUP'))
);

CREATE INDEX ix_person_registry_access_check ON usr.person_registry_access (check_id);
CREATE INDEX ix_person_registry_access_created ON usr.person_registry_access (created_at);

-- Resultado del cotejo con el registro en cada verificacion (informativo: el analista sigue decidiendo).
ALTER TABLE usr.identity_verification
    ADD COLUMN registry_status   varchar(20),
    ADD COLUMN registry_check_id uuid,
    ADD CONSTRAINT identity_verification_registry_check_fk FOREIGN KEY (registry_check_id) REFERENCES usr.person_registry_check (id),
    ADD CONSTRAINT identity_verification_registry_status_ck CHECK (registry_status IS NULL OR registry_status IN
        ('MATCH', 'NAME_MISMATCH', 'NOT_FOUND', 'DOCUMENT_EXPIRED', 'UNAVAILABLE', 'NOT_APPLICABLE'));
