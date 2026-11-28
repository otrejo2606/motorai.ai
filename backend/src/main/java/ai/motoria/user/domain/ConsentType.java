package ai.motoria.user.domain;

/**
 * Granular consents (Ley 8968). The text of each type and version lives in the frontend i18n files
 * under the key consent.{TYPE}.{version}.
 */
public enum ConsentType {
    DATA_PROCESSING,
    SHARE_INSURANCE_BROKER,
    SHARE_BANK,
    PHOTO_LOCATION,
    MODEL_TRAINING
}
