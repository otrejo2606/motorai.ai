package ai.motoria.vehicle.domain;

/**
 * Registry attributes (design D3). The owner is stored only as OWNER_ID_HASH (keyed hash) plus OWNER_NAME.
 */
public enum VehicleAttributeKey {
    BRAND,
    MODEL,
    STYLE,
    YEAR,
    COLOR,
    VIN,
    ENGINE_NUMBER,
    DISPLACEMENT_CC,
    FUEL,
    DRIVETRAIN,
    DOORS,
    CAPACITY,
    OWNER_ID_HASH,
    OWNER_NAME;

    public boolean isOwnerData() {
        return this == OWNER_ID_HASH || this == OWNER_NAME;
    }

    /**
     * Attributes that are never shown outside the backoffice (owner data and identifiers useful for cloning).
     */
    public boolean isPublic() {
        return !isOwnerData() && this != VIN && this != ENGINE_NUMBER;
    }
}
