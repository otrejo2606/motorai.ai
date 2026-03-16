# Motoria.ai — Backend Code Review

> Reviewer: Architect Agent + Security Agent
> Scope: `listing` module + `common` kernel
> Date: 2026-03-15

---

## Summary

| Category | Count | Severity |
|---|---|---|
| Architecture Violations | 12 | 3 Critical / 5 Major / 4 Minor |
| OWASP Issues | 10 | 4 Critical / 4 Major / 2 Minor |
| Anti-Patterns | 8 | 3 Major / 5 Minor |

---

## Architecture Violations

---

### AV-01 — `GlobalExceptionMapper` imports from `listing` module
**Severity:** Critical
**File:** `common/exception/GlobalExceptionMapper.java:3-4`

```java
import ai.motoria.listing.exception.ListingInvalidStateException;
import ai.motoria.listing.exception.ListingNotFoundException;
```

The `common` kernel imports concrete exceptions from the `listing` domain module. This reverses the dependency direction. `common` must know nothing about domain modules — domain modules depend on `common`, not the other way around.

**Fix:** Define base exception classes in `common` (e.g., `NotFoundException`, `InvalidStateException`). Domain exceptions extend those base types. `GlobalExceptionMapper` handles only base types.

```java
// common/exception/NotFoundException.java
public class NotFoundException extends RuntimeException { ... }

// listing/exception/ListingNotFoundException.java
public class ListingNotFoundException extends NotFoundException { ... }

// GlobalExceptionMapper.java — no listing import needed
if (exception instanceof NotFoundException e) {
    return build(Response.Status.NOT_FOUND, "NOT_FOUND", e.getMessage());
}
```

---

### AV-02 — `ListingRepository` receives a DTO
**Severity:** Critical
**File:** `listing/repository/ListingRepository.java:16`

```java
public List<Listing> search(ListingSearchRequest request) {
```

The repository directly accepts a DTO. Repositories must depend only on domain types and Java primitives — never on DTOs. This creates an illegal dependency from the persistence layer into the dto layer.

**Fix:** Decompose the DTO into explicit parameters or introduce a domain `ListingFilter` value object.

```java
// domain/ListingFilter.java (value object)
public record ListingFilter(ListingStatus status, ListingCategory category,
                            BigDecimal minPrice, BigDecimal maxPrice,
                            Integer modelYear, int page, int size) {}

// repository — receives domain type only
public List<Listing> search(ListingFilter filter) { ... }

// service — maps DTO to domain filter before calling repo
ListingFilter filter = listingMapper.toFilter(request);
```

---

### AV-03 — `AiPriceIntegration` contains hardcoded business logic
**Severity:** Major
**File:** `listing/integration/AiPriceIntegration.java:11-14`

```java
public PriceRange recommendPriceRange(BigDecimal basePrice) {
    BigDecimal offset = basePrice.multiply(BigDecimal.valueOf(0.1));
    return new PriceRange(basePrice.subtract(offset), basePrice.add(offset));
}
```

The integration layer is meant to wrap external system calls (HTTP, gRPC, SDK). This is a hardcoded ±10% formula — it is business logic disguised as an integration. It creates false confidence that AI pricing is operational when it is not.

**Fix:** Define an interface, implement a stub that throws `UnsupportedOperationException` or returns a clearly marked placeholder, and create the real implementation when the AI module HTTP client is available.

```java
public interface AiPriceIntegration {
    PriceRange recommendPriceRange(BigDecimal basePrice);
}

@ApplicationScoped
public class StubAiPriceIntegration implements AiPriceIntegration {
    @Override
    public PriceRange recommendPriceRange(BigDecimal basePrice) {
        // TODO: replace with real HTTP call to ai module
        return PriceRange.unavailable();
    }
}
```

---

### AV-04 — `ListingMapper.updateEntity()` calls domain behavior
**Severity:** Major
**File:** `listing/mapper/ListingMapper.java:22-29`

```java
default void updateEntity(UpdateListingRequest request, Listing listing) {
    listing.updateDetails(
            request.title(), request.description(),
            request.price(), request.modelYear(), request.mileage());
}
```

Mappers must only convert data. Calling `listing.updateDetails()` is domain behavior — it belongs in the service. The mapper is now responsible for both data mapping and triggering domain state changes.

**Fix:** Remove `updateEntity` from the mapper. Call `listing.updateDetails(...)` directly in `ListingService.update()`.

```java
// ListingService.java
public ListingResponse update(UUID listingId, UpdateListingRequest request) {
    Listing listing = findById(listingId);
    // domain method called by service, not by mapper
    listing.updateDetails(request.title(), request.description(),
                          request.price(), request.modelYear(), request.mileage());
    ...
}
```

---

### AV-05 — `CreateListingRequest` accepts `sellerId` from client
**Severity:** Critical
**File:** `listing/dto/CreateListingRequest.java:18`

```java
@NotNull UUID sellerId,
```

`sellerId` must never come from the request body. It must be extracted from the authenticated JWT token. Any authenticated seller can submit an arbitrary `sellerId` and create listings attributed to other users.

**Fix:** Remove `sellerId` from the DTO. Inject `JsonWebToken` in the service and extract the subject.

```java
// CreateListingRequest — remove sellerId field

// ListingService.java
@Inject
JsonWebToken jwt;

public ListingResponse create(CreateListingRequest request) {
    UUID sellerId = UUID.fromString(jwt.getSubject());
    Listing listing = listingMapper.toEntity(request);
    listing.setSellerId(sellerId);
    ...
}
```

---

### AV-06 — `Listing.initialize()` is public
**Severity:** Minor
**File:** `listing/domain/Listing.java:61`

`initialize()` is `public`, which allows any class to call it and re-initialize an existing listing (resetting its ID, status, and timestamps). The `@PrePersist` callback already covers the JPA lifecycle. The explicit call in the service is redundant and risky.

**Fix:** Make `initialize()` `private`. Let `@PrePersist` be the single initialization path, or use a factory method / constructor.

---

### AV-07 — `ListingEventConsumer` is an empty class
**Severity:** Minor
**File:** `listing/event/ListingEventConsumer.java`

```java
@ApplicationScoped
public class ListingEventConsumer {
}
```

An empty `@ApplicationScoped` bean. It is generated scaffolding with no content. This creates noise and false confidence that event consumption is implemented.

**Fix:** Either implement the consumer or delete the class until it has real content.

---

### AV-08 — All events share a single RabbitMQ routing key
**Severity:** Critical
**File:** `common/event/RabbitMqEventPublisher.java` + `application.properties:21`

```properties
mp.messaging.outgoing.motoria-events-out.default-routing-key=notification.send
```

The publisher uses a single `Emitter<String>` with one outgoing channel and a static default routing key. Every event — `listing.created`, `listing.published`, `finance.simulation.requested`, etc. — is sent with routing key `notification.send`. Topic-based routing in RabbitMQ never works. No consumer subscribed to `listing.created` will ever receive it.

**Fix:** Use `OutgoingRabbitMQMetadata` to set the routing key dynamically per message.

```java
@Override
public <T> void publish(EventType eventType, String sourceModule, T payload) {
    try {
        String json = objectMapper.writeValueAsString(
                DomainEventEnvelope.of(eventType, sourceModule, payload));
        Message<String> message = Message.of(json)
                .addMetadata(OutgoingRabbitMQMetadata.builder()
                        .withRoutingKey(eventType.routingKey())
                        .build());
        emitter.send(message);
    } catch (JsonProcessingException e) {
        throw new IllegalStateException("Unable to serialize event " + eventType.routingKey(), e);
    }
}
```

---

### AV-09 — No pagination on search
**Severity:** Major
**File:** `listing/repository/ListingRepository.java:41`

```java
return find(query.toString(), parameters).list();
```

`.list()` loads the entire result set into memory. On a marketplace with thousands of listings, this will exhaust heap memory and degrade response time.

**Fix:** Add `page` and `size` to the filter and use Panache pagination.

```java
return find(query.toString(), parameters).page(filter.page(), filter.size()).list();
```

---

### AV-10 — `quarkus.swagger-ui.always-include=true` in base config
**Severity:** Minor
**File:** `application.properties:27`

Swagger UI is always enabled regardless of environment. In production this exposes the full API surface to anyone who finds the `/q/swagger-ui` path.

**Fix:** Move to `application-dev.properties` only, or restrict via profile:

```properties
# application.properties
%prod.quarkus.swagger-ui.enable=false
%dev.quarkus.swagger-ui.always-include=true
```

---

### AV-11 — `ListingSearchRequest` uses public fields
**Severity:** Minor
**File:** `listing/dto/ListingSearchRequest.java:12-25`

All fields are `public` with no encapsulation, no validation, and no `@Min`/`@Max` on price ranges. JAX-RS `@BeanParam` works with private fields and getters.

---

### AV-12 — `@Audited` annotation missing `@Inherited`
**Severity:** Minor
**File:** `common/audit/Audited.java`

Without `@Inherited`, the CDI interceptor binding does not propagate to subclasses. CDI proxies extend the original class, which can silently drop the interceptor on some CDI implementations.

**Fix:**
```java
@Inherited
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {}
```

---

## OWASP Issues

---

### OWASP-01 — Hardcoded credentials (A07:2021 — Security Misconfiguration)
**Severity:** Critical
**File:** `application.properties:7-8,16,24-25`

```properties
quarkus.datasource.password=motoria
quarkus.oidc.credentials.secret=change-me
mp.messaging.outgoing.motoria-events-out.username=guest
mp.messaging.outgoing.motoria-events-out.password=guest
```

Database password, OIDC client secret, and RabbitMQ credentials are hardcoded in the properties file. This file will be committed to version control, exposing all credentials.

**Fix:** Use environment variable injection.

```properties
quarkus.datasource.password=${MOTORIA_DB_PASSWORD}
quarkus.oidc.credentials.secret=${MOTORIA_OIDC_SECRET}
mp.messaging.outgoing.motoria-events-out.username=${RABBITMQ_USER}
mp.messaging.outgoing.motoria-events-out.password=${RABBITMQ_PASSWORD}
```

---

### OWASP-02 — Seller impersonation / BOLA (A01:2021 — Broken Access Control)
**Severity:** Critical
**Files:** `CreateListingRequest.java:18`, `ListingService.java:39-44`

Already described in AV-05. Any authenticated `SELLER` can pass a different `sellerId` in the body and create listings attributed to another user. This is a classic Broken Object Level Authorization (BOLA) vulnerability.

---

### OWASP-03 — No ownership check on mutating operations (A01:2021)
**Severity:** Critical
**File:** `listing/service/ListingService.java:53,66,92,105`

`update()`, `submitForReview()`, `markSold()`, and `requestCertification()` fetch a listing by ID and operate on it without verifying that the authenticated seller is the owner. Any `SELLER` can update or mark-sold any other seller's listing.

**Fix:** Add an ownership guard in each mutating method.

```java
private void assertOwnership(Listing listing, UUID authenticatedSellerId) {
    if (!listing.getSellerId().equals(authenticatedSellerId)) {
        throw new ForbiddenException("Access denied to listing " + listing.getId());
    }
}
```

---

### OWASP-04 — Internal exception messages exposed to client (A09:2021)
**Severity:** Major
**File:** `common/exception/GlobalExceptionMapper.java:26`

```java
return build(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", exception.getMessage());
```

Raw exception messages from unhandled exceptions are returned in the response body. These can contain DB schema fragments, JPA query text, class names, or stack trace summaries — all useful to an attacker.

**Fix:** Log the full exception server-side, return only a generic message to the client.

```java
Log.error("Unhandled exception", exception);
return build(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
             "An unexpected error occurred. Reference: " + correlationId);
```

---

### OWASP-05 — CORS wildcard (A05:2021 — Security Misconfiguration)
**Severity:** Critical
**File:** `application.properties:2`

```properties
quarkus.http.cors=true
```

CORS is enabled with no `allowed-origins`, `allowed-methods`, or `allowed-headers` restrictions. Quarkus defaults to `*` for all when CORS is enabled without explicit configuration, effectively disabling cross-origin protection.

**Fix:**
```properties
quarkus.http.cors=true
quarkus.http.cors.origins=https://app.motoria.ai,https://admin.motoria.ai
quarkus.http.cors.methods=GET,POST,PUT,PATCH,DELETE,OPTIONS
quarkus.http.cors.headers=Authorization,Content-Type
quarkus.http.cors.access-control-allow-credentials=true
```

---

### OWASP-06 — Missing HSTS header (A05:2021)
**Severity:** Major
**File:** `common/security/SecurityHeadersFilter.java`

`Strict-Transport-Security` is absent. Without HSTS, browsers can be downgraded from HTTPS to HTTP via man-in-the-middle attacks.

**Fix:** Add to `SecurityHeadersFilter`:
```java
responseContext.getHeaders().add("Strict-Transport-Security",
        "max-age=31536000; includeSubDomains; preload");
```

---

### OWASP-07 — No rate limiting implemented (A04:2021 — Insecure Design)
**Severity:** Major
**File:** `listing/rest/ListingResource.java` — all endpoints

The architecture document specifies Redis sliding window rate limiting. No rate limiting exists in any code. The search endpoint accepts unlimited unauthenticated-equivalent calls. A single client can scrape all listings or perform enumeration attacks.

**Fix:** Implement a JAX-RS `ContainerRequestFilter` that reads from Redis per `(userId, endpoint)` and rejects with `HTTP 429` when the limit is exceeded.

---

### OWASP-08 — Audit log captures no actor, resource, or outcome (A09:2021)
**Severity:** Major
**File:** `common/audit/AuditInterceptor.java:16`

```java
Log.infov("Audit action {0}.{1}",
    context.getMethod().getDeclaringClass().getSimpleName(),
    context.getMethod().getName());
```

The audit log is a method-level invocation logger. It captures:
- What method was called: yes
- Who called it (user identity): NO
- What resource was affected (ID): NO
- Whether it succeeded or failed: NO
- What the arguments were: NO

This does not constitute a compliance-grade audit trail.

**Fix:** Inject `JsonWebToken`, capture user subject, method arguments, and wrap the `context.proceed()` to capture success or exception.

```java
@AroundInvoke
Object logAudit(InvocationContext context) throws Exception {
    String user = jwt != null ? jwt.getSubject() : "anonymous";
    String action = context.getMethod().getDeclaringClass().getSimpleName()
                  + "." + context.getMethod().getName();
    try {
        Object result = context.proceed();
        Log.infov("AUDIT OK  user={0} action={1}", user, action);
        return result;
    } catch (Exception e) {
        Log.warnv("AUDIT FAIL user={0} action={1} error={2}", user, action, e.getMessage());
        throw e;
    }
}
```

---

### OWASP-09 — No XSS sanitization on text fields (A03:2021 — Injection)
**Severity:** Minor
**Files:** `CreateListingRequest.java`, `UpdateListingRequest.java`

`title` and `description` accept arbitrary text with no HTML sanitization. If rendered in the frontend without explicit escaping (possible in some React scenarios with `dangerouslySetInnerHTML`), stored XSS is possible.

**Fix:** Add a custom `@SafeHtml` validator or strip HTML tags at the service layer using a library like OWASP Java HTML Sanitizer before persisting.

---

### OWASP-10 — `quarkus.http.auth.proactive=false` (A01:2021)
**Severity:** Minor
**File:** `application.properties:4`

Non-proactive authentication defers identity resolution until needed. Combined with missing global policy enforcement, endpoints that rely solely on `@RolesAllowed` without explicit security policy configuration may behave unexpectedly. Confirm that all non-public endpoints require active authentication.

---

## Anti-Patterns

---

### AP-01 — Entity has both public setters and domain behavior methods
**File:** `listing/domain/Listing.java:75-195`

`Listing` mixes two incompatible patterns:
- Domain behavior: `submitForReview()`, `publish()`, `markSold()` with state guards
- Raw public setters: `setTitle()`, `setPrice()`, `setSellerId()`, etc. with no guards

The setters bypass all invariants. `listing.setStatus(ListingStatus.SOLD)` would silently skip all the `ensureState()` guards in `markSold()`. Mappers or any other code that calls setters breaks the rich domain model.

**Fix:** Remove all public setters. Use the domain methods for all state changes. Use the constructor or a factory method for initial population from a DTO.

---

### AP-02 — Dual initialization path with dead `@PrePersist` guard
**File:** `listing/domain/Listing.java:61-73`

```java
public void initialize() { this.id = UUID.randomUUID(); ... }

@PrePersist
void prePersist() {
    if (id == null) { initialize(); } // never true if initialize() was called
}
```

`ListingService` calls `listing.initialize()` before persisting. After that call, `id` is never null, so `@PrePersist` never fires `initialize()`. The guard is dead code. If `initialize()` is removed or not called, `@PrePersist` silently picks it up — this ambiguity is confusing.

**Fix:** Remove the explicit `initialize()` call from the service. Let `@PrePersist` be the only initialization path. Make `initialize()` private.

---

### AP-03 — Event routing key not set per message
**File:** `common/event/RabbitMqEventPublisher.java`

Already detailed in AV-08. The anti-pattern: embedding the routing key in the payload JSON field while sending all messages with the same AMQP-level routing key. The event type field inside the JSON body is invisible to RabbitMQ's routing logic.

---

### AP-04 — Test suite covers only OpenAPI endpoint
**File:** `test/listing/rest/ListingResourceTest.java`

```java
void openApiEndpointShouldBeAvailable() { ... }
```

One test. It verifies `/q/openapi` returns 200. There are no tests for:
- Creating a listing (happy path)
- Validation failures
- State transition rules (`publish()` from `DRAFT` must fail)
- Authorization (`BUYER` cannot create a listing)
- Search returning correct results
- Not-found handling

A codebase this size with one smoke test is a test debt risk.

---

### AP-05 — `GlobalExceptionMapper` catches `Exception` without logging
**File:** `common/exception/GlobalExceptionMapper.java:13,26`

```java
implements ExceptionMapper<Exception>
```

The fallback `Exception` catch returns the error but never logs the stack trace. Production debugging becomes impossible because the server swallows the root cause silently.

**Fix:** Always log unhandled exceptions before returning the response.

---

### AP-06 — `AiPriceIntegration` called synchronously inside `@Transactional` create
**File:** `listing/service/ListingService.java:38-44`

```java
@Transactional
public ListingResponse create(CreateListingRequest request) {
    Listing listing = listingMapper.toEntity(request);
    listing.initialize();
    listing.applyRecommendedPriceRange(aiPriceIntegration.recommendPriceRange(request.price()));
    listingRepository.persist(listing);
    ...
}
```

An external AI call is made inside an open database transaction. If the AI service is slow or unavailable, the transaction hangs open and holds a DB connection, reducing connection pool availability.

**Fix:** Call the AI integration before opening the transaction, or make the price recommendation asynchronous (fire event → AI responds via `ai.price.recommended` event).

---

### AP-07 — `ListingSearchRequest` public fields
**File:** `listing/dto/ListingSearchRequest.java`

Already noted in AV-11. Public fields on a DTO break encapsulation and prevent adding validation logic or computed properties later without breaking callers.

---

### AP-08 — `prePersist` and `initialize` visibility inconsistency
**File:** `listing/domain/Listing.java:69`

`prePersist()` is package-private (no modifier) while `initialize()` is `public`. For JPA lifecycle callbacks, package-private or `protected` is correct. The asymmetry is accidental and misleading.

---

## Prioritized Fix Backlog

| Priority | ID | Issue | File |
|---|---|---|---|
| P0 | OWASP-02 | Seller impersonation via sellerId in body | CreateListingRequest, ListingService |
| P0 | OWASP-03 | No ownership check on mutating operations | ListingService |
| P0 | AV-08 | All events sent with same routing key | RabbitMqEventPublisher |
| P0 | OWASP-01 | Hardcoded credentials | application.properties |
| P0 | OWASP-05 | CORS wildcard | application.properties |
| P1 | AV-01 | common imports listing exceptions | GlobalExceptionMapper |
| P1 | AV-02 | Repository receives DTO | ListingRepository |
| P1 | OWASP-04 | Exception message leakage | GlobalExceptionMapper |
| P1 | OWASP-08 | Audit log missing actor and outcome | AuditInterceptor |
| P1 | OWASP-07 | No rate limiting | ListingResource |
| P1 | AP-01 | Entity mixes setters and domain methods | Listing |
| P2 | AV-03 | Fake integration with hardcoded logic | AiPriceIntegration |
| P2 | AV-04 | Mapper calls domain behavior | ListingMapper |
| P2 | AV-09 | Unbounded search result | ListingRepository |
| P2 | OWASP-06 | Missing HSTS header | SecurityHeadersFilter |
| P2 | AP-06 | External call inside transaction | ListingService |
| P3 | AV-05 | initialize() is public | Listing |
| P3 | AP-04 | Insufficient test coverage | ListingResourceTest |
| P3 | AV-10 | Swagger always-include in prod | application.properties |
| P3 | AV-12 | @Audited missing @Inherited | Audited |

---

*Generated by Architect Agent + Security Agent — Motoria.ai*
