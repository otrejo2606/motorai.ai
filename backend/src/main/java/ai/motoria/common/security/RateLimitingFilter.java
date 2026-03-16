package ai.motoria.common.security;

import ai.motoria.common.exception.ErrorResponse;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Duration;
import java.time.Instant;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class RateLimitingFilter implements ContainerRequestFilter {

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    JsonWebToken jwt;

    @Inject
    Instance<RoutingContext> routingContextInstance;

    @ConfigProperty(name = "motoria.rate-limit.requests-per-minute", defaultValue = "100")
    int requestsPerMinute;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (path.startsWith("/q/health") || path.startsWith("/q/metrics")) {
            return;
        }

        String discriminator = resolveClientDiscriminator(requestContext);
        String key = "ratelimit:" + discriminator + ":" + path;

        ValueCommands<String, Long> values = redisDataSource.value(Long.class);
        KeyCommands<String> keys = redisDataSource.key();
        long current = values.incr(key);
        if (current == 1L) {
            keys.expire(key, Duration.ofMinutes(1));
        }

        if (current > requestsPerMinute) {
            requestContext.abortWith(Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse("RATE_LIMIT_EXCEEDED", "Too many requests", Instant.now()))
                    .build());
        }
    }

    private String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private String resolveClientDiscriminator(ContainerRequestContext requestContext) {
        String subject = jwt != null ? jwt.getSubject() : null;
        if (subject != null && !subject.isBlank()) {
            return subject;
        }

        String forwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = requestContext.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        String forwarded = requestContext.getHeaderString("Forwarded");
        if (forwarded != null && !forwarded.isBlank()) {
            for (String part : forwarded.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith("for=")) {
                    return trimmed.substring(4).replace("\"", "").trim();
                }
            }
        }

        if (routingContextInstance.isResolvable() && routingContextInstance.get().request().remoteAddress() != null) {
            return routingContextInstance.get().request().remoteAddress().hostAddress();
        }

        return "unknown-client";
    }
}