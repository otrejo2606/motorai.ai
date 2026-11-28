-- V1: baseline of the schema that Hibernate generated with generation=update before Flyway (design D2).
-- It mirrors the current mapping exactly: Listing (schema listing) and OutboxEvent (default schema).
-- Listing had no explicit column names and Quarkus used the Hibernate default physical naming
-- (identity), so PostgreSQL folded camelCase names to lower case: sellerid, vehiclespecid, modelyear,
-- createdat, updatedat. V6 renames them to snake_case.
-- On a database that already has these tables, Flyway baselines at version 1 and skips this file
-- (baseline-on-migrate); IF NOT EXISTS keeps it harmless if it ever runs on top of them.

CREATE SCHEMA IF NOT EXISTS listing;

CREATE TABLE IF NOT EXISTS listing.listing (
    id            uuid                        NOT NULL,
    title         varchar(120)                NOT NULL,
    description   varchar(1024)               NOT NULL,
    status        varchar(20)                 NOT NULL,
    category      varchar(20)                 NOT NULL,
    sellerid      uuid                        NOT NULL,
    vehiclespecid uuid                        NOT NULL,
    price         numeric(18, 2)              NOT NULL,
    price_min     numeric(38, 2)              NOT NULL,
    price_max     numeric(38, 2)              NOT NULL,
    modelyear     integer                     NOT NULL,
    mileage       integer                     NOT NULL,
    createdat     timestamp(6) with time zone NOT NULL,
    updatedat     timestamp(6) with time zone NOT NULL,
    CONSTRAINT listing_pkey PRIMARY KEY (id),
    CONSTRAINT listing_status_check CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'SOLD', 'EXPIRED', 'REJECTED')),
    CONSTRAINT listing_category_check CHECK (category IN ('CAR', 'MOTORCYCLE', 'TRUCK', 'VAN'))
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id             uuid                        NOT NULL,
    event_type     varchar(120)                NOT NULL,
    source_module  varchar(60)                 NOT NULL,
    aggregate_id   uuid                        NOT NULL,
    correlation_id uuid                        NOT NULL,
    payload_json   text                        NOT NULL,
    occurred_at    timestamp(6) with time zone NOT NULL,
    created_at     timestamp(6) with time zone NOT NULL,
    available_at   timestamp(6) with time zone NOT NULL,
    locked_until   timestamp(6) with time zone,
    sent_at        timestamp(6) with time zone,
    status         varchar(20)                 NOT NULL,
    retry_count    integer                     NOT NULL,
    last_error     varchar(1024),
    CONSTRAINT outbox_event_pkey PRIMARY KEY (id),
    CONSTRAINT outbox_event_correlation_id_key UNIQUE (correlation_id),
    CONSTRAINT outbox_event_status_check CHECK (status IN ('PENDING', 'IN_PROGRESS', 'FAILED', 'SENT'))
);
