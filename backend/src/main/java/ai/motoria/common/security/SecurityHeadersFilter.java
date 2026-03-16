package ai.motoria.common.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        responseContext.getHeaders().add("Content-Security-Policy", "default-src 'self'");
        responseContext.getHeaders().add("X-Content-Type-Options", "nosniff");
        responseContext.getHeaders().add("X-Frame-Options", "DENY");
        responseContext.getHeaders().add("Referrer-Policy", "strict-origin-when-cross-origin");
        responseContext.getHeaders().add("Permissions-Policy", "geolocation=(), microphone=()");
        responseContext.getHeaders().add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }
}