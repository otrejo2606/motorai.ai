-- V10: validacion del angulo de cada foto del anuncio por un clasificador local (add-captura-guiada-fotos).
-- F1 reservo V1 a V19 (design D2 de add-mvp-placa-publicacion); V9 es la consulta del vehiculo por placa; F2 usa V20 a V39.
-- El resultado vive en la propia foto del anuncio (listing.listing_photo, que ya tiene las columnas de auditoria
-- created_at, created_by, updated_at y updated_by). No se guarda la imagen ni la respuesta cruda del clasificador:
-- solo la toma detectada, la confianza, la version del modelo, la latencia y los valores leidos (VIN y odometro).

ALTER TABLE listing.listing_photo
    ADD COLUMN angle_status        varchar(20)  NOT NULL DEFAULT 'NOT_CHECKED',
    ADD COLUMN detected_slot       varchar(30),
    ADD COLUMN angle_confidence    numeric(5, 4),
    ADD COLUMN angle_reason        varchar(60),
    ADD COLUMN angle_model_version varchar(60),
    ADD COLUMN angle_latency_ms    bigint,
    ADD COLUMN angle_checked_at    timestamptz,
    ADD COLUMN read_vin            varchar(30),
    ADD COLUMN read_odometer_km    integer,
    ADD COLUMN reading_anomaly     varchar(30),
    ADD COLUMN review_decision     varchar(20),
    ADD COLUMN review_reason       varchar(30),
    ADD COLUMN reviewed_by         uuid,
    ADD COLUMN reviewed_at         timestamptz,
    ADD CONSTRAINT listing_photo_angle_status_ck
        CHECK (angle_status IN ('NOT_CHECKED', 'PENDING', 'MATCH', 'MISMATCH', 'UNCERTAIN', 'UNAVAILABLE')),
    ADD CONSTRAINT listing_photo_angle_confidence_ck
        CHECK (angle_confidence IS NULL OR (angle_confidence >= 0 AND angle_confidence <= 1)),
    ADD CONSTRAINT listing_photo_read_odometer_ck
        CHECK (read_odometer_km IS NULL OR read_odometer_km >= 0),
    ADD CONSTRAINT listing_photo_reading_anomaly_ck
        CHECK (reading_anomaly IS NULL OR reading_anomaly IN ('VIN_MISMATCH', 'ODOMETER_MISMATCH')),
    ADD CONSTRAINT listing_photo_review_decision_ck
        CHECK (review_decision IS NULL OR review_decision IN ('ACCEPTED', 'REJECTED')),
    ADD CONSTRAINT listing_photo_review_reason_ck
        CHECK (review_reason IS NULL OR review_reason IN ('WRONG_ANGLE', 'BLURRY', 'TOO_DARK', 'OBSTRUCTED', 'NOT_THE_VEHICLE', 'OTHER'));

-- Bandeja del analista: fotos UNCERTAIN o UNAVAILABLE, o con una anomalia de lectura, sin decision todavia.
CREATE INDEX ix_listing_photo_angle_review ON listing.listing_photo (updated_at)
    WHERE review_decision IS NULL AND (angle_status IN ('UNCERTAIN', 'UNAVAILABLE') OR reading_anomaly IS NOT NULL);
