package ai.motoria.vehicle.domain;

/**
 * Where the registry data of a vehicle came from: the registry PDF read by the AI and the analyst (DOCUMENT) or the
 * registry agreement queried by plate (AGREEMENT, Credid). Each source has its own maximum age. Vehicles registered
 * before V9 have no source and are treated as DOCUMENT.
 */
public enum RegistrySource {
    DOCUMENT,
    AGREEMENT
}
