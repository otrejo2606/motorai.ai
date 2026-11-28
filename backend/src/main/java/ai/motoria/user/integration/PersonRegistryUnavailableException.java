package ai.motoria.user.integration;

import ai.motoria.common.exception.DependencyUnavailableException;

/**
 * The person registry could not answer (not configured, credentials rejected, error or timeout). HTTP 503.
 */
public class PersonRegistryUnavailableException extends DependencyUnavailableException {

    public PersonRegistryUnavailableException(String code, String message) {
        super(code, message);
    }

    public PersonRegistryUnavailableException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
