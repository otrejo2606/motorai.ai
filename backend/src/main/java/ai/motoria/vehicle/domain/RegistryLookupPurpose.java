package ai.motoria.vehicle.domain;

/**
 * Why the vehicle registry was queried by plate (logged in vehicle.registry_lookup).
 * TRANSFER_BEFORE and TRANSFER_AFTER are reserved for the transfer flow (F5): a fresh query before signing and another
 * one after the sale to record the new owner.
 */
public enum RegistryLookupPurpose {
    SELLER_LOOKUP,
    TRANSFER_BEFORE,
    TRANSFER_AFTER,
    ANALYST
}
