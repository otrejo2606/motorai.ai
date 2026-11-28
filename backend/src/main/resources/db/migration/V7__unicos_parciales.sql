-- V7: partial unique indexes that close two concurrency windows, plus expiry of legacy duplicates (no new table, so no audit columns).
-- The services translate a violation into a stable error code (LISTING_ALREADY_EXISTS, REGISTRY_REQUEST_ALREADY_OPEN).

-- One active listing per seller and vehicle.
-- Legacy rows: before F1 nothing prevented several active listings per seller and vehicle_spec_id (a model id,
-- copied into vehicle_id by V6). Keep the most recently updated one and expire the rest so the index can be
-- created and the application starts. Expired rows keep all their data; F1 has no reactivation path (manual SQL if needed).
-- Before deploying, the owner can list them on the database copy (task 0.4) with:
-- SELECT seller_id, vehicle_id, count(*) FROM listing.listing
-- WHERE status IN ('DRAFT','PENDING_REVIEW','PUBLISHED') GROUP BY 1, 2 HAVING count(*) > 1;
UPDATE listing.listing SET status = 'EXPIRED', updated_at = now(), updated_by = 'system'
WHERE id IN (
    SELECT d.id FROM (
        SELECT id, row_number() OVER (PARTITION BY seller_id, vehicle_id ORDER BY updated_at DESC, id DESC) AS rn
        FROM listing.listing
        WHERE status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED')
    ) d
    WHERE d.rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_listing_active_seller_vehicle
    ON listing.listing (seller_id, vehicle_id)
    WHERE status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED');

-- One open "que Motoria lo obtenga" request per vehicle.
CREATE UNIQUE INDEX IF NOT EXISTS ux_registry_request_pending_vehicle
    ON vehicle.registry_request (vehicle_id)
    WHERE status = 'PENDING';
