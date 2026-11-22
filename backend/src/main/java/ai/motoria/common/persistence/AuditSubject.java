package ai.motoria.common.persistence;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;

/**
 * Resolves the authenticated subject (Keycloak {@code sub}) used to fill the audit columns.
 * Falls back to {@code system} outside a request (consumers, schedulers, unit tests).
 */
public final class AuditSubject {

    public static final String SYSTEM = "system";
    private static final int MAX_LENGTH = 255;

    private AuditSubject() {
    }

    public static String current() {
        try {
            ArcContainer container = Arc.container();
            if (container == null || !container.requestContext().isActive()) {
                return SYSTEM;
            }
            InstanceHandle<SecurityIdentity> handle = container.instance(SecurityIdentity.class);
            if (!handle.isAvailable()) {
                return SYSTEM;
            }
            SecurityIdentity identity = handle.get();
            if (identity == null || identity.isAnonymous()) {
                return SYSTEM;
            }
            return truncate(subjectOf(identity.getPrincipal()));
        } catch (RuntimeException exception) {
            return SYSTEM;
        }
    }

    private static String subjectOf(Principal principal) {
        if (principal instanceof JsonWebToken token && token.getSubject() != null) {
            return token.getSubject();
        }
        if (principal != null && principal.getName() != null) {
            return principal.getName();
        }
        return SYSTEM;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
