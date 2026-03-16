# Motoria.ai — Formal Code Review Report

```
Agent   : review-agent.md
Skill   : code-review.md     → Architecture · Security · Performance · Modularity
Prompt  : review-code.md     → Architecture compliance · Security · Anti-patterns
Date    : 2026-03-15
Scope   : listing module + common kernel (all generated .java files + application.properties)
```

---

## Overall Verdict

```
STATUS: REJECTED
```

The review agent rejects the submission on three grounds:

| Rejection Criterion | Result |
|---|---|
| Business logic in controllers | PASS — controllers are thin and delegate correctly |
| Missing validation | FAIL — ownership not validated; sellerId not validated against token; search unguarded |
| Architecture violations | FAIL — common imports from listing; repository receives DTO; mapper calls domain behavior; event routing broken |

Code cannot proceed to integration until all P0 and P1 findings below are resolved.

---

## 1 — Architecture Compliance

> Skill: Architecture · Prompt: Architecture compliance

### FAIL — `common` kernel imports `listing` domain exceptions

**File:** `common/exception/GlobalExceptionMapper.java:3–4`

```java
import ai.motoria.listing.exception.ListingInvalidStateException;
import ai.motoria.listing.exception.ListingNotFoundException;
```

The dependency arrow is inverted. The rule is: domain modules depend on `common`; `common` must never depend on domain modules. As modules beyond `listing` are implemented, this file will accumulate an import per module and become a god class.

Required fix — define base exception types in `common`:

```java
// common/exception/NotFoundException.java
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}

// common/exception/InvalidStateException.java
public class InvalidStateException extends RuntimeException {
    public InvalidStateException(String message) { super(message); }
}
```

Domain exceptions extend the base:

```java
// listing/exception/ListingNotFoundException.java
public class ListingNotFoundException extends NotFoundException {
    public ListingNotFoundException(UUID id) {
        super("Listing " + id + " was not found");
    }
}
```

`GlobalExceptionMapper` handles only base types. No listing import required.

---

### FAIL — Repository receives a DTO

**File:** `listing/repository/ListingRepository.java:16`

```java
public List<Listing> search(ListingSearchRequest request) {
```

The repository layer imports and accepts a DTO. Repositories must depend only on domain types and primitives. This creates a layering violation: the persistence layer now knows about the HTTP/REST boundary.

Required fix — introduce a domain value object:

```java
// listing/domain/ListingFilter.java
public record ListingFilter(
    ListingStatus status,
    ListingCategory category,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    Integer modelYear,
    int page,
    int size) {}
```

Repository signature becomes:

```java
public List<Listing> search(ListingFilter filter) { ... }
```

The service maps the DTO to the filter before calling the repository.

---

### FAIL — Mapper calls domain behavior

**File:** `listing/mapper/ListingMapper.java:22–29`

```java
default void updateEntity(UpdateListingRequest request, Listing listing) {
    listing.updateDetails(
            request.title(), request.description(),
            request.price(), request.modelYear(), request.mileage());
}
```

Mappers must only convert data between types. Calling `listing.updateDetails()` is domain behavior. By placing it here, the mapper acquires responsibility for triggering state changes — violating single responsibility and the layer contract.

Required fix — remove `updateEntity` from mapper. The service calls the domain method directly:

```java
// ListingService.java
public ListingResponse update(UUID listingId, UpdateListingRequest request) {
    Listing listing = findById(listingId);
    listing.updateDetails(
        request.title(), request.description(),
        request.price(), request.modelYear(), request.mileage());
    ...
}
```

---

### FAIL — All events share one static AMQP routing key

**File:** `common/event/RabbitMqEventPublisher.java` + `application.properties:21`

```properties
mp.messaging.outgoing.motoria-events-out.default-routing-key=notification.send
```

The publisher serializes `EventType` into the JSON payload body but sends every message with routing key `notification.send` at the AMQP level. RabbitMQ routes by the AMQP routing key, not by JSON content. No consumer subscribed to `listing.created`, `inspection.scheduled`, or any other topic will receive messages. The entire event-driven architecture is non-functional as written.

Required fix — set routing key per message using `OutgoingRabbitMQMetadata`:

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
        throw new IllegalStateException("Cannot serialize event: " + eventType.routingKey(), e);
    }
}
```

Remove `default-routing-key` from `application.properties`.

---

### FAIL — `Listing` entity exposes public setters alongside domain behavior

**File:** `listing/domain/Listing.java:165–195`

The entity implements rich domain behavior with state guards (`submitForReview`, `publish`, `markSold`, `ensureState`) but simultaneously exposes raw public setters that bypass all guards:

```java
public void setPrice(BigDecimal price) { this.price = price; }
public void setMileage(Integer mileage) { this.mileage = mileage; }
// ... etc
```

Any code holding a `Listing` reference can mutate state silently without going through domain invariants. This is an anemic domain model leak grafted onto a rich domain model — both patterns in the same class, which is worse than either alone.

Required fix — remove all public setters. Populate the entity from the DTO exclusively via a factory method or constructor called by the mapper:

```java
public static Listing create(UUID sellerId, UUID vehicleSpecId, ListingCategory category,
                              String title, String description, BigDecimal price,
                              Integer modelYear, Integer mileage) {
    Listing l = new Listing();
    l.id = UUID.randomUUID();
    l.status = ListingStatus.DRAFT;
    // ... assign all fields
    return l;
}
```

---

### FAIL — `initialize()` is public with redundant `@PrePersist`

**File:** `listing/domain/Listing.java:61–73`

`ListingService` calls `listing.initialize()` explicitly. `@PrePersist` then checks `if (id == null)` — which is never true because `initialize()` already set the id. The `@PrePersist` is dead code. The public visibility of `initialize()` allows re-initialization of an existing entity.

Required fix — remove explicit `initialize()` call from the service. Use a factory method (above). Keep `@PrePersist` as the sole safety net and make it `private`.

---

### FAIL — `ListingEventConsumer` is empty scaffolding

**File:** `listing/event/ListingEventConsumer.java`

```java
@ApplicationScoped
public class ListingEventConsumer {
}
```

An `@ApplicationScoped` bean with no content creates noise and suggests functionality that does not exist. Delete it until it has real content.

---

### WARN — `@Audited` missing `@Inherited`

**File:** `common/audit/Audited.java`

CDI interceptor bindings should declare `@Inherited` so the binding propagates through CDI proxies and subclasses. Without it, the interceptor may silently drop on proxied types in certain CDI implementations.

```java
@Inherited
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {}
```

---

## 2 — Security

> Skill: Security · Prompt: Security (OWASP)

### CRITICAL — Hardcoded credentials (OWASP A07:2021 — Security Misconfiguration)

**File:** `application.properties:7–8, 16, 24–25`

```properties
quarkus.datasource.password=motoria
quarkus.oidc.credentials.secret=change-me
mp.messaging.outgoing.motoria-events-out.username=guest
mp.messaging.outgoing.motoria-events-out.password=guest
```

All secrets are hardcoded in a file that will be committed to version control. The OIDC secret is literally `change-me`.

Required fix — externalize all secrets:

```properties
quarkus.datasource.password=${MOTORIA_DB_PASSWORD}
quarkus.oidc.credentials.secret=${MOTORIA_OIDC_SECRET}
mp.messaging.outgoing.motoria-events-out.username=${RABBITMQ_USER}
mp.messaging.outgoing.motoria-events-out.password=${RABBITMQ_PASSWORD}
```

---

### CRITICAL — Seller impersonation via client-supplied `sellerId` (OWASP A01:2021 — BOLA)

**File:** `listing/dto/CreateListingRequest.java:18`

```java
@NotNull UUID sellerId,
```

The `sellerId` is accepted from the HTTP request body. Any authenticated seller can set an arbitrary `sellerId` and create listings on behalf of another user. This is Broken Object Level Authorization.

Required fix — remove `sellerId` from the DTO. Extract from the authenticated JWT in the service:

```java
@Inject JsonWebToken jwt;

public ListingResponse create(CreateListingRequest request) {
    UUID sellerId = UUID.fromString(jwt.getSubject());
    Listing listing = Listing.create(sellerId, ...);
    ...
}
```

---

### CRITICAL — No ownership check on mutating operations (OWASP A01:2021 — Broken Access Control)

**File:** `listing/service/ListingService.java:53, 66, 92, 105`

`update()`, `submitForReview()`, `markSold()`, and `requestCertification()` load the listing by ID and operate without verifying the authenticated caller is the owner. Any `SELLER` can modify another seller's listing.

Required fix — add an ownership assertion to all mutating methods:

```java
private void assertOwnership(Listing listing) {
    UUID callerId = UUID.fromString(jwt.getSubject());
    if (!listing.getSellerId().equals(callerId)) {
        throw new ForbiddenOperationException("Access denied to listing " + listing.getId());
    }
}
```

Call `assertOwnership(listing)` immediately after `findById(listingId)` in every mutating service method.

---

### CRITICAL — CORS wildcard (OWASP A05:2021 — Security Misconfiguration)

**File:** `application.properties:2`

```properties
quarkus.http.cors=true
```

CORS enabled with no `allowed-origins` defaults to allowing all origins (`*`). This breaks cross-origin protection entirely.

Required fix:

```properties
quarkus.http.cors=true
quarkus.http.cors.origins=https://app.motoria.ai,https://admin.motoria.ai
quarkus.http.cors.methods=GET,POST,PUT,PATCH,DELETE,OPTIONS
quarkus.http.cors.headers=Authorization,Content-Type
quarkus.http.cors.access-control-allow-credentials=true
```

---

### HIGH — Internal exception message exposed to client (OWASP A09:2021)

**File:** `common/exception/GlobalExceptionMapper.java:26`

```java
return build(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", exception.getMessage());
```

The raw message of any unhandled exception is returned to the client. JPA exceptions, query fragments, and class names can be exposed.

Required fix — log server-side, return a generic safe message:

```java
Log.error("Unhandled exception", exception);
return build(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
             "An unexpected error occurred.");
```

---

### HIGH — Missing `Strict-Transport-Security` header (OWASP A05:2021)

**File:** `common/security/SecurityHeadersFilter.java`

`SecurityHeadersFilter` adds CSP, `X-Content-Type-Options`, `X-Frame-Options`, and `Referrer-Policy` but omits HSTS. Without it, HTTPS downgrade attacks are possible.

Required fix:

```java
responseContext.getHeaders().add("Strict-Transport-Security",
    "max-age=31536000; includeSubDomains; preload");
```

---

### HIGH — Rate limiting not implemented (OWASP A04:2021 — Insecure Design)

The architecture defines Redis sliding window rate limiting per `(userId, endpoint)`. No rate limiting filter or interceptor exists. The search endpoint is fully unguarded and can be scraped or abused without limit.

Required fix — implement a `ContainerRequestFilter` that increments a Redis counter per caller and returns HTTP 429 when exceeded.

---

### HIGH — Audit interceptor captures no actor, resource, or outcome (OWASP A09:2021)

**File:** `common/audit/AuditInterceptor.java:16`

```java
Log.infov("Audit action {0}.{1}",
    context.getMethod().getDeclaringClass().getSimpleName(),
    context.getMethod().getName());
```

The audit log records only the method name. It captures no user identity, no resource ID, no input summary, and no outcome (success or exception). This is a method invocation log, not an audit trail.

Required fix — inject `JsonWebToken`, capture the caller, wrap `proceed()` to catch failures:

```java
@Inject JsonWebToken jwt;

@AroundInvoke
Object logAudit(InvocationContext ctx) throws Exception {
    String user = jwt != null ? jwt.getSubject() : "anonymous";
    String action = ctx.getMethod().getDeclaringClass().getSimpleName()
                  + "." + ctx.getMethod().getName();
    try {
        Object result = ctx.proceed();
        Log.infov("AUDIT OK  user={0} action={1}", user, action);
        return result;
    } catch (Exception e) {
        Log.warnv("AUDIT FAIL user={0} action={1} error={2}", user, action, e.getMessage());
        throw e;
    }
}
```

---

### MEDIUM — Swagger UI always enabled (OWASP A05:2021)

**File:** `application.properties:27`

```properties
quarkus.swagger-ui.always-include=true
```

In production this exposes the full API surface publicly. Must be profile-restricted:

```properties
%prod.quarkus.swagger-ui.enable=false
%dev.quarkus.swagger-ui.always-include=true
```

---

### MEDIUM — Text fields have no HTML sanitization (OWASP A03:2021 — Injection)

**File:** `CreateListingRequest.java`, `UpdateListingRequest.java`

`title` and `description` validate size and blank constraints but accept raw HTML. If content is rendered without escaping (e.g., React `dangerouslySetInnerHTML`), stored XSS is possible. Sanitize at the service layer using OWASP Java HTML Sanitizer before persisting.

---

### LOW — Non-proactive auth defers identity resolution (OWASP A01:2021)

**File:** `application.properties:4`

```properties
quarkus.http.auth.proactive=false
```

Combined with missing ownership guards, deferring authentication can result in unauthenticated code paths that rely solely on `@RolesAllowed`. Confirm all endpoints have a valid security policy and no gaps exist between proactive and reactive resolution.

---

## 3 — Performance

> Skill: Performance

### FAIL — Unbounded result set on search

**File:** `listing/repository/ListingRepository.java:41`

```java
return find(query.toString(), parameters).list();
```

`.list()` loads all matching rows into the JVM heap. On a marketplace with thousands of listings, a single search request can exhaust memory and degrade the entire node. There is no `page`, `size`, or result cap anywhere in the call chain.

Required fix — add pagination to `ListingFilter` and use Panache page API:

```java
return find(query.toString(), parameters)
    .page(filter.page(), filter.size())
    .list();
```

Default `size` should be capped (e.g., max 50 per page).

---

### FAIL — External AI call inside open `@Transactional`

**File:** `listing/service/ListingService.java:38–44`

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

An external call (`aiPriceIntegration`) is made while an open database transaction holds a connection from the pool. If the AI service is slow or unavailable, the transaction hangs and the connection pool drains, eventually cascading into a DB timeout for all concurrent requests.

Required fix — call the external service before opening the transaction, or make price recommendation asynchronous (publish event → consume `ai.price.recommended`):

```java
public ListingResponse create(CreateListingRequest request) {
    PriceRange priceRange = aiPriceIntegration.recommendPriceRange(request.price()); // outside tx
    return persistListing(request, priceRange); // @Transactional method
}
```

---

### WARN — No caching on vehicle catalog lookups

The architecture mandates Redis caching on vehicle catalog data (TTL 24h). No `@CacheResult` or Redis reads exist in the generated code. Every listing response that returns `vehicleSpecId` forces a downstream DB or service roundtrip (once other modules are implemented).

Required fix — apply `@CacheResult(cacheName = "vehicle-spec")` on the vehicle catalog service method once the vehicle module is implemented.

---

### WARN — HashMap used for query parameters — no ordering guarantee

**File:** `listing/repository/ListingRepository.java:18`

```java
Map<String, Object> parameters = new HashMap<>();
```

Panache's named parameter binding is correct regardless of map order, so this is not a functional bug. However, using `LinkedHashMap` or `Map.of(...)` makes query construction more predictable and testable.

---

## 4 — Modularity

> Skill: Modularity

### FAIL — 11 of 12 modules are empty scaffolding

`ai`, `certification`, `financing`, `inspection`, `media`, `notification`, `partner`, `promotion`, `subscription`, `user`, `vehicle` contain only `package-info.java` placeholder files. No domain classes, no services, no repositories, no events. The module boundaries are declared but not implemented. The only functional module is `listing`.

This is scaffolding, not a backend. Cross-module contracts (event consumers, integration ports) cannot be validated until at minimum `certification`, `notification`, and `ai` are implemented, as `listing` depends on all three.

---

### FAIL — `AiPriceIntegration` is not an integration — it is a hardcoded formula

**File:** `listing/integration/AiPriceIntegration.java:11–14`

```java
public PriceRange recommendPriceRange(BigDecimal basePrice) {
    BigDecimal offset = basePrice.multiply(BigDecimal.valueOf(0.1));
    return new PriceRange(basePrice.subtract(offset), basePrice.add(offset));
}
```

This is a concrete class with a hardcoded ±10% formula. No interface exists. No real HTTP call is made to the `ai` module. The integration layer is meant to isolate external boundaries behind an interface so implementations can be swapped.

Required fix — define an interface, provide a clearly-named stub, and implement the real HTTP adapter separately:

```java
public interface AiPriceIntegration {
    PriceRange recommendPriceRange(BigDecimal basePrice);
}

@ApplicationScoped
@Named("stub")
public class StubAiPriceIntegration implements AiPriceIntegration { ... }

@ApplicationScoped
@Named("http")
public class HttpAiPriceIntegration implements AiPriceIntegration { ... }
```

---

### FAIL — Cross-module communication missing for certification, notification, promotion

The architecture defines:
- `listing` → publishes `listing.certification.requested`
- `certification` → must consume and respond

The consumer side (`CertificationEventConsumer`, `NotificationEventConsumer`, `PromotionEventConsumer`) does not exist. Events are published into a void with no consumer. This must be tracked as missing modularity.

---

### PASS — `common` kernel design is sound

`DomainEventPublisher` (interface), `DomainEventEnvelope` (record), `EventType` (enum), `Audited` + `AuditInterceptor`, `SecurityHeadersFilter`, `GlobalExceptionMapper` — the common kernel correctly avoids domain-specific logic (apart from the listing import identified in AV-01). The approach of one emitter interface implemented by a RabbitMQ adapter is correct.

---

### PASS — `listing` module layer separation is correct where implemented

- `ListingResource` contains no business logic — PASS
- `ListingService` contains business logic only — PASS
- `ListingRepository` contains persistence logic only — PASS (apart from receiving a DTO)
- `ListingMapper` is a MapStruct interface — PASS (apart from `updateEntity`)
- `ListingEventProducer` delegates to `DomainEventPublisher` — PASS

---

## 5 — Anti-Patterns

> Prompt: Anti-patterns

| # | Pattern | Location | Description |
|---|---|---|---|
| AP-01 | Mixed domain model | `Listing.java` | Rich domain methods and raw public setters coexist. Setters bypass all invariants. |
| AP-02 | Dead guard code | `Listing.java:70–72` | `@PrePersist` guard `if (id == null)` is never true after explicit `initialize()` call. |
| AP-03 | Payload routing key | `RabbitMqEventPublisher.java` | EventType serialized in JSON body but AMQP routing key fixed. Topic routing non-functional. |
| AP-04 | Empty bean | `ListingEventConsumer.java` | `@ApplicationScoped` with no content. Implies behavior that does not exist. |
| AP-05 | Silent exception swallow | `GlobalExceptionMapper.java:26` | Unhandled exceptions returned to client without server-side log. Debugging impossible. |
| AP-06 | Fake integration | `AiPriceIntegration.java` | Named "integration", contains hardcoded arithmetic, no interface, no real call. |
| AP-07 | Anemic search DTO | `ListingSearchRequest.java` | Public mutable fields, no encapsulation, no validation, no pagination support. |
| AP-08 | One smoke test | `ListingResourceTest.java` | Entire module covered by a single OpenAPI availability check. Zero domain test coverage. |

---

## Rejection Checklist (Review Agent)

| Check | Result | Blocker |
|---|---|---|
| Business logic in controllers | PASS | No |
| Missing validation — input DTOs | PASS — DTOs have `@Valid`, `@NotNull`, `@Size` | No |
| Missing validation — ownership | FAIL — no JWT subject check against `sellerId` | YES |
| Missing validation — search bounds | FAIL — no pagination, no result cap | YES |
| Architecture violations | FAIL — 6 violations listed above | YES |

---

## Prioritized Fix Backlog

```
BLOCK (required before merge)
  SEC-01  Hardcoded credentials → use environment variables
  SEC-02  sellerId from request body → extract from JWT
  SEC-03  No ownership guard on update/submitForReview/markSold/requestCertification
  SEC-04  CORS wildcard → restrict to known origins
  ARCH-01 common imports listing exceptions → base exception hierarchy
  ARCH-04 Event routing key not set per message → OutgoingRabbitMQMetadata

P1 (required before first release)
  ARCH-02 Repository receives DTO → ListingFilter value object
  ARCH-03 Mapper calls domain behavior → move to service
  ARCH-05 Entity public setters removed → factory method / constructor
  SEC-05  Raw exception message in error response → generic message + server log
  SEC-06  Missing HSTS header
  SEC-07  Rate limiting not implemented
  SEC-08  Audit interceptor incomplete → add actor, resource, outcome
  PERF-01 Unbounded search result → add pagination
  PERF-02 External call inside @Transactional → call before transaction opens

P2 (before next module implementation)
  MOD-01  AiPriceIntegration → define interface, name stub clearly
  MOD-02  Implement certification, notification event consumers
  ARCH-06 initialize() public + dead @PrePersist guard → factory method
  ARCH-07 Empty ListingEventConsumer → delete
  AP-08   Write domain tests for create, update, state transitions, auth

P3 (technical debt)
  SEC-09  Swagger always-include → profile-restrict to dev only
  ARCH-08 @Audited add @Inherited
  AP-07   ListingSearchRequest public fields → private with getters
```

---

*Review Agent — Motoria.ai Backend v1.0-SNAPSHOT*
*Verdict: REJECTED — resolve all BLOCK items before resubmission*
