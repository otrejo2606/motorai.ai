-- V4: module vehicle (design D3). Media and AI references are plain UUIDs (no cross-schema FK).
-- The registry owner is stored only as a keyed hash of the identity number plus the name.

CREATE SCHEMA IF NOT EXISTS vehicle;

CREATE TABLE vehicle.vehicle (
    id                         uuid         NOT NULL,
    country_code               varchar(2)   NOT NULL,
    plate                      varchar(20)  NOT NULL,
    status                     varchar(30)  NOT NULL,
    transfer_blocked           boolean      NOT NULL,
    transfer_block_reason      varchar(500),
    registry_checked_at        timestamptz,
    registry_document_media_id uuid,
    registry_document_date     date,
    extraction_inference_id    uuid,
    review_reason              varchar(40),
    rejection_reason           varchar(500),
    requested_by               uuid         NOT NULL,
    created_at                 timestamptz  NOT NULL,
    created_by                 varchar(255) NOT NULL,
    updated_at                 timestamptz  NOT NULL,
    updated_by                 varchar(255) NOT NULL,
    CONSTRAINT vehicle_pkey PRIMARY KEY (id),
    CONSTRAINT vehicle_country_plate_key UNIQUE (country_code, plate)
);

CREATE INDEX ix_vehicle_status ON vehicle.vehicle (status, updated_at);

CREATE TABLE vehicle.vehicle_attribute (
    id                uuid         NOT NULL,
    vehicle_id        uuid         NOT NULL,
    attr_key          varchar(30)  NOT NULL,
    attr_value        varchar(500) NOT NULL,
    source            varchar(30)  NOT NULL,
    evidence_media_id uuid,
    evidence_page     integer,
    confidence        numeric(5, 4),
    observed_at       timestamptz  NOT NULL,
    ai_value          varchar(500),
    corrected_by      varchar(255),
    created_at        timestamptz  NOT NULL,
    created_by        varchar(255) NOT NULL,
    updated_at        timestamptz  NOT NULL,
    updated_by        varchar(255) NOT NULL,
    CONSTRAINT vehicle_attribute_pkey PRIMARY KEY (id),
    CONSTRAINT vehicle_attribute_vehicle_fk FOREIGN KEY (vehicle_id) REFERENCES vehicle.vehicle (id),
    CONSTRAINT vehicle_attribute_vehicle_key_key UNIQUE (vehicle_id, attr_key)
);

CREATE TABLE vehicle.registry_encumbrance (
    id                uuid          NOT NULL,
    vehicle_id        uuid          NOT NULL,
    encumbrance_type  varchar(20)   NOT NULL,
    description       varchar(1000),
    blocks_transfer   boolean       NOT NULL,
    evidence_media_id uuid,
    evidence_page     integer,
    confidence        numeric(5, 4),
    created_at        timestamptz   NOT NULL,
    created_by        varchar(255)  NOT NULL,
    updated_at        timestamptz   NOT NULL,
    updated_by        varchar(255)  NOT NULL,
    CONSTRAINT registry_encumbrance_pkey PRIMARY KEY (id),
    CONSTRAINT registry_encumbrance_vehicle_fk FOREIGN KEY (vehicle_id) REFERENCES vehicle.vehicle (id)
);

CREATE INDEX ix_registry_encumbrance_vehicle ON vehicle.registry_encumbrance (vehicle_id);

CREATE TABLE vehicle.registry_discrepancy (
    id             uuid         NOT NULL,
    vehicle_id     uuid         NOT NULL,
    listing_id     uuid,
    attr_key       varchar(30)  NOT NULL,
    registry_value varchar(500),
    declared_value varchar(500),
    status         varchar(20)  NOT NULL,
    created_at     timestamptz  NOT NULL,
    created_by     varchar(255) NOT NULL,
    updated_at     timestamptz  NOT NULL,
    updated_by     varchar(255) NOT NULL,
    CONSTRAINT registry_discrepancy_pkey PRIMARY KEY (id),
    CONSTRAINT registry_discrepancy_vehicle_fk FOREIGN KEY (vehicle_id) REFERENCES vehicle.vehicle (id)
);

CREATE INDEX ix_registry_discrepancy_vehicle ON vehicle.registry_discrepancy (vehicle_id, status);

-- "Que Motoria lo obtenga": an operator buys the registry report and uploads it.
CREATE TABLE vehicle.registry_request (
    id                 uuid           NOT NULL,
    vehicle_id         uuid           NOT NULL,
    requested_by       uuid           NOT NULL,
    accepted_fee       numeric(12, 2) NOT NULL,
    currency           varchar(3)     NOT NULL,
    status             varchar(20)    NOT NULL,
    document_media_id  uuid,
    fulfilled_by       varchar(255),
    fulfilled_at       timestamptz,
    created_at         timestamptz    NOT NULL,
    created_by         varchar(255)   NOT NULL,
    updated_at         timestamptz    NOT NULL,
    updated_by         varchar(255)   NOT NULL,
    CONSTRAINT registry_request_pkey PRIMARY KEY (id),
    CONSTRAINT registry_request_vehicle_fk FOREIGN KEY (vehicle_id) REFERENCES vehicle.vehicle (id)
);

CREATE INDEX ix_registry_request_status ON vehicle.registry_request (status, created_at);
CREATE INDEX ix_registry_request_vehicle ON vehicle.registry_request (vehicle_id, status);
