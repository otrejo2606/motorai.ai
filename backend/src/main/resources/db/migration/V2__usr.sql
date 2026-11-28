-- V2: module user (schema usr; "user" is reserved in PostgreSQL). Design D3 and D1.
-- The identity number and its type are encrypted at rest (AES-GCM, key from the environment);
-- id_number_hash is a keyed HMAC used to compare with the registry owner.

CREATE SCHEMA IF NOT EXISTS usr;

CREATE TABLE usr.user_profile (
    id                     uuid         NOT NULL,
    full_name              varchar(200),
    id_type_enc            varchar(200),
    id_number_enc          varchar(300),
    id_number_hash         varchar(64),
    id_number_last4        varchar(4),
    phone                  varchar(30),
    email                  varchar(254),
    country_code           varchar(2)   NOT NULL,
    identity_level         varchar(10)  NOT NULL,
    verification_provider  varchar(40),
    verification_reference varchar(120),
    created_at             timestamptz  NOT NULL,
    created_by             varchar(255) NOT NULL,
    updated_at             timestamptz  NOT NULL,
    updated_by             varchar(255) NOT NULL,
    CONSTRAINT user_profile_pkey PRIMARY KEY (id)
);

CREATE INDEX ix_user_profile_id_number_hash ON usr.user_profile (id_number_hash);

-- Append-only ledger: the current state of a consent type is its latest row.
CREATE TABLE usr.consent (
    id           uuid         NOT NULL,
    user_id      uuid         NOT NULL,
    consent_type varchar(40)  NOT NULL,
    text_version varchar(20)  NOT NULL,
    granted      boolean      NOT NULL,
    decided_at   timestamptz  NOT NULL,
    ip_address   varchar(45),
    created_at   timestamptz  NOT NULL,
    created_by   varchar(255) NOT NULL,
    updated_at   timestamptz  NOT NULL,
    updated_by   varchar(255) NOT NULL,
    CONSTRAINT consent_pkey PRIMARY KEY (id),
    CONSTRAINT consent_user_fk FOREIGN KEY (user_id) REFERENCES usr.user_profile (id)
);

CREATE INDEX ix_consent_user_type ON usr.consent (user_id, consent_type, decided_at);

-- Manual identity verification (design D1): document both sides and a selfie holding the document
-- next to a one-time challenge code; a BACKOFFICE analyst approves or rejects.
CREATE TABLE usr.identity_verification (
    id                      uuid         NOT NULL,
    user_id                 uuid         NOT NULL,
    provider                varchar(40)  NOT NULL,
    status                  varchar(20)  NOT NULL,
    challenge_code          varchar(12)  NOT NULL,
    challenge_expires_at    timestamptz  NOT NULL,
    document_front_media_id uuid,
    document_back_media_id  uuid,
    selfie_media_id         uuid,
    submitted_at            timestamptz,
    reviewed_by             varchar(255),
    reviewed_at             timestamptz,
    rejection_reason        varchar(500),
    created_at              timestamptz  NOT NULL,
    created_by              varchar(255) NOT NULL,
    updated_at              timestamptz  NOT NULL,
    updated_by              varchar(255) NOT NULL,
    CONSTRAINT identity_verification_pkey PRIMARY KEY (id),
    CONSTRAINT identity_verification_user_fk FOREIGN KEY (user_id) REFERENCES usr.user_profile (id)
);

CREATE INDEX ix_identity_verification_user ON usr.identity_verification (user_id, created_at);
CREATE INDEX ix_identity_verification_status ON usr.identity_verification (status, submitted_at);
