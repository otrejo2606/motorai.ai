-- V6: listing linked to vehicle (design D3, D7). Renames the legacy lower-case columns created by
-- Hibernate (sellerid, vehiclespecid, modelyear, createdat, updatedat) to snake_case only when they
-- exist, so it works both after V1 and on a database baselined from generation=update.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'listing' AND table_name = 'listing' AND column_name = 'sellerid') THEN
        ALTER TABLE listing.listing RENAME COLUMN sellerid TO seller_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'listing' AND table_name = 'listing' AND column_name = 'vehiclespecid') THEN
        ALTER TABLE listing.listing RENAME COLUMN vehiclespecid TO vehicle_spec_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'listing' AND table_name = 'listing' AND column_name = 'modelyear') THEN
        ALTER TABLE listing.listing RENAME COLUMN modelyear TO model_year;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'listing' AND table_name = 'listing' AND column_name = 'createdat') THEN
        ALTER TABLE listing.listing RENAME COLUMN createdat TO created_at;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'listing' AND table_name = 'listing' AND column_name = 'updatedat') THEN
        ALTER TABLE listing.listing RENAME COLUMN updatedat TO updated_at;
    END IF;
END
$$;

ALTER TABLE listing.listing
    ADD COLUMN IF NOT EXISTS vehicle_id          uuid,
    ADD COLUMN IF NOT EXISTS brand               varchar(80),
    ADD COLUMN IF NOT EXISTS model               varchar(80),
    ADD COLUMN IF NOT EXISTS fuel                varchar(40),
    ADD COLUMN IF NOT EXISTS country_code        varchar(2),
    ADD COLUMN IF NOT EXISTS currency            varchar(3),
    ADD COLUMN IF NOT EXISTS province            varchar(40),
    ADD COLUMN IF NOT EXISTS transmission        varchar(20),
    ADD COLUMN IF NOT EXISTS trust_level         varchar(30),
    ADD COLUMN IF NOT EXISTS declared_equipment  jsonb,
    ADD COLUMN IF NOT EXISTS maintenance_history varchar(2000),
    ADD COLUMN IF NOT EXISTS negotiable          boolean,
    ADD COLUMN IF NOT EXISTS published_at        timestamptz,
    ADD COLUMN IF NOT EXISTS created_by          varchar(255),
    ADD COLUMN IF NOT EXISTS updated_by          varchar(255);

-- vehicle_spec_id had no entity behind it: it is copied into vehicle_id and no longer used.
UPDATE listing.listing SET vehicle_id = vehicle_spec_id WHERE vehicle_id IS NULL;
-- Backfill of legacy rows (created before F1) so the NOT NULL constraints below hold:
-- F1 only operates in CR with CRC as default currency (design D9.1); audit subject 'system'.
-- Legacy vehicle_id values point to no vehicle.vehicle row; the listing services tolerate it
-- (the vehicle lookup returns empty and the listing keeps trust_level NONE).
UPDATE listing.listing SET trust_level = 'NONE' WHERE trust_level IS NULL;
UPDATE listing.listing SET negotiable = false WHERE negotiable IS NULL;
UPDATE listing.listing SET declared_equipment = '[]'::jsonb WHERE declared_equipment IS NULL;
UPDATE listing.listing SET country_code = 'CR' WHERE country_code IS NULL;
UPDATE listing.listing SET currency = 'CRC' WHERE currency IS NULL;
UPDATE listing.listing SET created_by = 'system' WHERE created_by IS NULL;
UPDATE listing.listing SET updated_by = 'system' WHERE updated_by IS NULL;

ALTER TABLE listing.listing
    ALTER COLUMN vehicle_spec_id DROP NOT NULL,
    ALTER COLUMN model_year DROP NOT NULL,
    ALTER COLUMN vehicle_id SET NOT NULL,
    ALTER COLUMN trust_level SET NOT NULL,
    ALTER COLUMN negotiable SET NOT NULL,
    ALTER COLUMN declared_equipment SET NOT NULL,
    ALTER COLUMN country_code SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL,
    ALTER COLUMN created_by SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_listing_status_updated ON listing.listing (status, updated_at);
CREATE INDEX IF NOT EXISTS ix_listing_seller ON listing.listing (seller_id, status);
CREATE INDEX IF NOT EXISTS ix_listing_vehicle ON listing.listing (vehicle_id);

CREATE TABLE listing.listing_photo (
    id         uuid          NOT NULL,
    listing_id uuid          NOT NULL,
    slot       varchar(30)   NOT NULL,
    media_id   uuid          NOT NULL,
    public_url varchar(1000) NOT NULL,
    created_at timestamptz   NOT NULL,
    created_by varchar(255)  NOT NULL,
    updated_at timestamptz   NOT NULL,
    updated_by varchar(255)  NOT NULL,
    CONSTRAINT listing_photo_pkey PRIMARY KEY (id),
    CONSTRAINT listing_photo_listing_fk FOREIGN KEY (listing_id) REFERENCES listing.listing (id),
    CONSTRAINT listing_photo_listing_slot_key UNIQUE (listing_id, slot)
);
