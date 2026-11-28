-- V9: consulta del vehiculo por placa en el registro (Credid), primera opcion del vendedor; el PDF queda como contingencia.
-- F1 reservo V1 a V19 (design D2 de add-mvp-placa-publicacion); V8 es la consulta de personas; F2 usa V20 a V39.
-- No se guarda la respuesta cruda ni la identificacion del dueno en claro (va como OWNER_ID_HASH en vehicle_attribute).

-- Origen de los datos registrales del vehiculo: DOCUMENT (PDF) o AGREEMENT (convenio). Cada origen tiene su vigencia
-- (document-max-age P30D, agreement-max-age P90D). Las filas anteriores quedan en NULL y se tratan como DOCUMENT.
ALTER TABLE vehicle.vehicle
    ADD COLUMN registry_source varchar(20),
    ADD CONSTRAINT vehicle_registry_source_ck CHECK (registry_source IS NULL OR registry_source IN ('DOCUMENT', 'AGREEMENT'));

-- Bitacora de cada llamada al registro por placa: proveedor, resultado, clase de placa, motivo y quien la pidio.
-- Sirve para el cupo diario por proveedor (solo cuentan FOUND y NOT_FOUND) y como historial del vehiculo.
CREATE TABLE vehicle.registry_lookup (
    id                 uuid         NOT NULL,
    vehicle_id         uuid         NOT NULL,
    provider           varchar(40)  NOT NULL,
    purpose            varchar(30)  NOT NULL,
    outcome            varchar(20)  NOT NULL,
    error_code         varchar(60),
    plate_class        varchar(5)   NOT NULL,
    provider_reference varchar(60),
    requested_by       uuid,
    checked_at         timestamptz  NOT NULL,
    created_at         timestamptz  NOT NULL,
    created_by         varchar(255) NOT NULL,
    updated_at         timestamptz  NOT NULL,
    updated_by         varchar(255) NOT NULL,
    CONSTRAINT registry_lookup_pkey PRIMARY KEY (id),
    CONSTRAINT registry_lookup_vehicle_fk FOREIGN KEY (vehicle_id) REFERENCES vehicle.vehicle (id),
    CONSTRAINT registry_lookup_purpose_ck CHECK (purpose IN ('SELLER_LOOKUP', 'TRANSFER_BEFORE', 'TRANSFER_AFTER', 'ANALYST')),
    CONSTRAINT registry_lookup_outcome_ck CHECK (outcome IN ('FOUND', 'NOT_FOUND', 'FAILED')),
    CONSTRAINT registry_lookup_plate_class_ck CHECK (plate_class IN ('A', 'C', 'CL', 'D'))
);

-- Cupo diario de consultas por proveedor y dia de Costa Rica.
CREATE INDEX ix_registry_lookup_provider_checked ON vehicle.registry_lookup (provider, checked_at);
-- Historial de consultas de un vehiculo.
CREATE INDEX ix_registry_lookup_vehicle ON vehicle.registry_lookup (vehicle_id, checked_at);
