package ai.motoria.user.exception;

/**
 * Stable error codes of the user module (the frontend translates them).
 */
public final class UserErrorCodes {

    public static final String PROFILE_NOT_FOUND = "PROFILE_NOT_FOUND";
    public static final String PROFILE_CONCURRENT_CREATION = "PROFILE_CONCURRENT_CREATION";
    public static final String PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE";
    public static final String PROFILE_ID_NUMBER_INVALID = "PROFILE_ID_NUMBER_INVALID";
    public static final String PROFILE_ID_NUMBER_LOCKED = "PROFILE_ID_NUMBER_LOCKED";
    public static final String PROFILE_ID_TYPE_REQUIRED = "PROFILE_ID_TYPE_REQUIRED";
    public static final String USER_CRYPTO_KEY_MISSING = "USER_CRYPTO_KEY_MISSING";
    public static final String USER_CRYPTO_FAILED = "USER_CRYPTO_FAILED";
    public static final String IDENTITY_ALREADY_VERIFIED = "IDENTITY_ALREADY_VERIFIED";
    public static final String IDENTITY_VERIFICATION_PENDING = "IDENTITY_VERIFICATION_PENDING";
    public static final String IDENTITY_VERIFICATION_NOT_FOUND = "IDENTITY_VERIFICATION_NOT_FOUND";
    public static final String IDENTITY_VERIFICATION_INVALID_STATE = "IDENTITY_VERIFICATION_INVALID_STATE";
    public static final String IDENTITY_CHALLENGE_EXPIRED = "IDENTITY_CHALLENGE_EXPIRED";
    public static final String IDENTITY_MEDIA_INVALID = "IDENTITY_MEDIA_INVALID";
    public static final String IDENTITY_MANUAL_QUOTA_EXHAUSTED = "IDENTITY_MANUAL_QUOTA_EXHAUSTED";
    public static final String IDENTITY_SELF_REVIEW_FORBIDDEN = "IDENTITY_SELF_REVIEW_FORBIDDEN";

    private UserErrorCodes() {
    }
}
