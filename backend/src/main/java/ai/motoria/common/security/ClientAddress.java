package ai.motoria.common.security;

import io.vertx.core.http.HttpServerRequest;

/**
 * Resolves the client IP for consent records (Ley 8968). Behind the gateway the first
 * X-Forwarded-For entry is used.
 */
public final class ClientAddress {

    private static final int MAX_LENGTH = 45;

    private ClientAddress() {
    }

    public static String resolve(HttpServerRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return truncate(forwardedFor.split(",")[0].trim());
        }
        if (request.remoteAddress() != null) {
            return truncate(request.remoteAddress().hostAddress());
        }
        return null;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_LENGTH ? value : value.substring(0, MAX_LENGTH);
    }
}
