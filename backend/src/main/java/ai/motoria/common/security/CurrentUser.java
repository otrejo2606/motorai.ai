package ai.motoria.common.security;

import ai.motoria.common.exception.ForbiddenOperationException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Optional;
import java.util.UUID;

/**
 * Caller of the current request. The user id is the Keycloak {@code sub} claim (a UUID);
 * roles come from the Keycloak realm roles ({@code realm_access/roles}).
 */
@RequestScoped
public class CurrentUser {

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    public boolean isAuthenticated() {
        return identity != null && !identity.isAnonymous();
    }

    public UUID id() {
        String subject = jwt != null ? jwt.getSubject() : null;
        if (subject == null && isAuthenticated() && identity.getPrincipal() != null) {
            subject = identity.getPrincipal().getName();
        }
        if (subject == null) {
            throw new ForbiddenOperationException("AUTH_REQUIRED", "An authenticated user is required");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new ForbiddenOperationException("AUTH_SUBJECT_INVALID", "The token subject is not a valid user id");
        }
    }

    public Optional<UUID> optionalId() {
        if (!isAuthenticated()) {
            return Optional.empty();
        }
        try {
            return Optional.of(id());
        } catch (ForbiddenOperationException exception) {
            return Optional.empty();
        }
    }

    public boolean hasRole(String role) {
        return isAuthenticated() && identity.hasRole(role);
    }

    public boolean isBackoffice() {
        return hasRole(Roles.BACKOFFICE) || hasRole(Roles.ADMIN);
    }

    public Optional<String> email() {
        if (jwt == null) {
            return Optional.empty();
        }
        Object email = jwt.getClaim("email");
        return email == null ? Optional.empty() : Optional.of(email.toString());
    }
}
