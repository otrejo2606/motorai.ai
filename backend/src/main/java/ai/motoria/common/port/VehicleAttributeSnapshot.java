package ai.motoria.common.port;

/**
 * @param source   REGISTRY_DOCUMENT, REGISTRY_AGREEMENT, SELLER_DECLARED or ANALYST
 * @param verified true when the value comes from an official source and the vehicle is REGISTRY_VERIFIED
 */
public record VehicleAttributeSnapshot(
        String key,
        String value,
        String source,
        boolean verified) {
}
