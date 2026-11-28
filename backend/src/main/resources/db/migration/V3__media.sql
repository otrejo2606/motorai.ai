-- V3: module media (design D3 and D5). Goes before vehicle because registry evidence references media objects.

CREATE SCHEMA IF NOT EXISTS media;

CREATE TABLE media.media_object (
    id               uuid         NOT NULL,
    owner_id         uuid         NOT NULL,
    kind             varchar(30)  NOT NULL,
    photo_slot       varchar(30),
    storage_key      varchar(300) NOT NULL,
    public_key       varchar(300),
    content_type     varchar(60)  NOT NULL,
    declared_size    bigint       NOT NULL,
    size_bytes       bigint,
    sha256           varchar(64),
    width            integer,
    height           integer,
    capture_metadata varchar(2000),
    visibility       varchar(10)  NOT NULL,
    status           varchar(20)  NOT NULL,
    confirmed_at     timestamptz,
    rejection_code   varchar(60),
    source_media_id  uuid,
    page_number      integer,
    created_at       timestamptz  NOT NULL,
    created_by       varchar(255) NOT NULL,
    updated_at       timestamptz  NOT NULL,
    updated_by       varchar(255) NOT NULL,
    CONSTRAINT media_object_pkey PRIMARY KEY (id),
    CONSTRAINT media_object_storage_key_key UNIQUE (storage_key)
);

CREATE INDEX ix_media_object_owner ON media.media_object (owner_id, kind);
CREATE INDEX ix_media_object_source ON media.media_object (source_media_id, page_number);

CREATE TABLE media.photo_protocol_slot (
    id              uuid         NOT NULL,
    country_code    varchar(2)   NOT NULL,
    code            varchar(30)  NOT NULL,
    required        boolean      NOT NULL,
    display_order   integer      NOT NULL,
    instruction_key varchar(120) NOT NULL,
    created_at      timestamptz  NOT NULL,
    created_by      varchar(255) NOT NULL,
    updated_at      timestamptz  NOT NULL,
    updated_by      varchar(255) NOT NULL,
    CONSTRAINT photo_protocol_slot_pkey PRIMARY KEY (id),
    CONSTRAINT photo_protocol_slot_country_code_key UNIQUE (country_code, code)
);

-- Audited private reads (design D5): every read URL or server-side content read.
CREATE TABLE media.media_access_log (
    id          uuid         NOT NULL,
    media_id    uuid         NOT NULL,
    accessed_by varchar(255) NOT NULL,
    purpose     varchar(60)  NOT NULL,
    accessed_at timestamptz  NOT NULL,
    created_at  timestamptz  NOT NULL,
    created_by  varchar(255) NOT NULL,
    updated_at  timestamptz  NOT NULL,
    updated_by  varchar(255) NOT NULL,
    CONSTRAINT media_access_log_pkey PRIMARY KEY (id),
    CONSTRAINT media_access_log_media_fk FOREIGN KEY (media_id) REFERENCES media.media_object (id)
);

CREATE INDEX ix_media_access_log_media ON media.media_access_log (media_id, accessed_at);

-- Costa Rica photo protocol seed (design D3). Editable later by ADMIN.
INSERT INTO media.photo_protocol_slot (id, country_code, code, required, display_order, instruction_key, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 'CR', 'FRONT',              true,  1,  'photoProtocol.slots.FRONT.instruction',              now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'REAR',               true,  2,  'photoProtocol.slots.REAR.instruction',               now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'LEFT',               true,  3,  'photoProtocol.slots.LEFT.instruction',               now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'RIGHT',              true,  4,  'photoProtocol.slots.RIGHT.instruction',              now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'FRONT_LEFT',         true,  5,  'photoProtocol.slots.FRONT_LEFT.instruction',         now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'FRONT_RIGHT',        true,  6,  'photoProtocol.slots.FRONT_RIGHT.instruction',        now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'REAR_LEFT',          true,  7,  'photoProtocol.slots.REAR_LEFT.instruction',          now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'REAR_RIGHT',         true,  8,  'photoProtocol.slots.REAR_RIGHT.instruction',         now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'INTERIOR_FRONT',     true,  9,  'photoProtocol.slots.INTERIOR_FRONT.instruction',     now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'INTERIOR_REAR',      false, 10, 'photoProtocol.slots.INTERIOR_REAR.instruction',      now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'DASHBOARD_ODOMETER', true,  11, 'photoProtocol.slots.DASHBOARD_ODOMETER.instruction', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'ENGINE',             true,  12, 'photoProtocol.slots.ENGINE.instruction',             now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'TIRES',              true,  13, 'photoProtocol.slots.TIRES.instruction',              now(), 'system', now(), 'system'),
    (gen_random_uuid(), 'CR', 'VIN',                true,  14, 'photoProtocol.slots.VIN.instruction',                now(), 'system', now(), 'system')
ON CONFLICT (country_code, code) DO NOTHING;
