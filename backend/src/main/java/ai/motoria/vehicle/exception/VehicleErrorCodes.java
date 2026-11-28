package ai.motoria.vehicle.exception;

/**
 * Stable error codes of the vehicle module (the frontend translates them).
 */
public final class VehicleErrorCodes {

    public static final String VEHICLE_NOT_FOUND = "VEHICLE_NOT_FOUND";
    public static final String VEHICLE_PLATE_INVALID = "VEHICLE_PLATE_INVALID";
    public static final String VEHICLE_COUNTRY_UNSUPPORTED = "VEHICLE_COUNTRY_UNSUPPORTED";
    public static final String VEHICLE_INVALID_STATE = "VEHICLE_INVALID_STATE";
    public static final String VEHICLE_DOCUMENT_INVALID = "VEHICLE_DOCUMENT_INVALID";
    public static final String VEHICLE_REQUIRED_ATTRIBUTES_MISSING = "VEHICLE_REQUIRED_ATTRIBUTES_MISSING";
    public static final String VEHICLE_ATTRIBUTE_UNKNOWN = "VEHICLE_ATTRIBUTE_UNKNOWN";
    public static final String VEHICLE_SELF_REVIEW_FORBIDDEN = "VEHICLE_SELF_REVIEW_FORBIDDEN";
    public static final String VEHICLE_CONCURRENT_CREATION = "VEHICLE_CONCURRENT_CREATION";
    public static final String VEHICLE_REGISTRY_NOT_CONFIGURED = "VEHICLE_REGISTRY_NOT_CONFIGURED";
    public static final String VEHICLE_REGISTRY_UNAVAILABLE = "VEHICLE_REGISTRY_UNAVAILABLE";
    public static final String VEHICLE_REGISTRY_QUOTA_EXHAUSTED = "VEHICLE_REGISTRY_QUOTA_EXHAUSTED";
    public static final String REGISTRY_REQUEST_NOT_AVAILABLE = "REGISTRY_REQUEST_NOT_AVAILABLE";
    public static final String REGISTRY_REQUEST_FEE_MISMATCH = "REGISTRY_REQUEST_FEE_MISMATCH";
    public static final String REGISTRY_REQUEST_ALREADY_OPEN = "REGISTRY_REQUEST_ALREADY_OPEN";
    public static final String REGISTRY_REQUEST_NOT_FOUND = "REGISTRY_REQUEST_NOT_FOUND";
    public static final String REGISTRY_REQUEST_INVALID_STATE = "REGISTRY_REQUEST_INVALID_STATE";

    private VehicleErrorCodes() {
    }
}
