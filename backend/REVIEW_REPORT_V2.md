# Motoria.ai — Backend Re-Review Report (Post-Remediation)

```
Agent   : review-agent.md
Skills  : code-review.md · security-owasp.md
Prompt  : review-code.md
Source  : REVIEW_REPORT.md (previous) + REMEDIATION_PLAN.md
Date    : 2026-03-15
Scope   : Full backend — all updated .java files + application.properties + pom.xml
```

---

## Overall Verdict

```
STATUS: REJECTED
```

All six original BLOCK items are resolved. However, the refactor introduced one critical regression that re-blocks the submission, plus two functional bugs in the rate limiting implementation. Unresolved items from the previous report are classified and prioritized below.

---

## Section 1 — BLOCK Item Validation

> Review-agent criterion: architecture violations must be absent.

---

### SEC-01 — Hardcoded credentials
**Previous status:** BLOCK
**Current status:** ✅ RESOLVED

`application.properties` correctly externalizes all secrets via environment variable expressions. No credential value remains in the file.

```properties
quarkus.datasource.password=${MOTORIA_DB_PASSWORD}
quarkus.oidc.credentials.secret=${MOTORIA_OIDC_SECRET}
mp.messaging.outgoing.motoria-events-out.username=${RABBITMQ_USER}
mp.messaging.outgoing.motoria-events-out.password=${RABBITMQ_PASSWORD}
```

---

### SEC-04 — CORS wildcard
**Previous status:** BLOCK
**Current status:** ✅ RESOLVED

CORS is now restricted to explicit origins with methods, headers, credentials, and max-age defined.

```properties
quarkus.http.cors.origins=https://app.motoria.ai,https://admin.motoria.ai,https://backoffice.motoria.ai
quarkus.http.cors.methods=GET,POST,PUT,PATCH,DELETE,OPTIONS
quarkus.http.cors.headers=Authorization,Content-Type,Accept
```

---

### SEC-02 — Seller impersonation via `sellerId` in request body
**Previous status:** BLOCK
**Current status:** ✅ RESOLVED

`CreateListingRequest` no longer contains a `sellerId` field. The service extracts the seller identity from the authenticated JWT token.

```java
// CreateListingRequest — sellerId removed ✓
// ListingService.create()
UUID sellerId = UUID.fromString(jwt.getSubject());
```

---

### SEC-03 — No ownership check on mutating operations
**Previous status:** BLOCK
**Current status:** ✅ RESOLVED

`assertOwnership(Listing listing)` is implemented correctly and called in:
- `applyUpdate()` — covers `update()` ✓
- `submitForReview()` ✓
- `markSold()` ✓
- `requestCertification()` ✓
- NOT called in `publish()` — correct, `@RolesAllowed({"BACKOFFICE","ADMIN"})` is the guard ✓
- NOT called in `getById()` — correct, read operation ✓

---

### ARCH-01 — `common` imports `listing` exceptions
**Previous status:** BLOCK
**Current status:** ✅ RESOLVED

`GlobalExceptionMapper` has zero imports from `ai.motoria.listing`. It handles:
- `NotFoundException` — HTTP 404
- `InvalidStateException` — HTTP 409
- `ForbiddenOperationException` — HTTP 403
- `ConstraintViolationException` — HTTP 400
- Fallback `Exception` — HTTP 500 with `Log.error` and generic message

Exception hierarchy is correctly established in `common`:

```
RuntimeException
├── NotFoundException           (common)
│   └── ListingNotFoundException (listing)
├── InvalidStateException       (common)
│   └── ListingInvalidStateException (listing)
└── ForbiddenOperationException (common)
```

---

### ARCH-04 — All events sent with static routing key
**Previous status:** BLOCK
**Current status:** ✅ RESOLVED

`RabbitMqEventPublisher` now sets the routing key per message using `OutgoingRabbitMQMetadata`:

```java
Message<String> message = Message.of(json)
    .addMetadata(OutgoingRabbitMQMetadata.builder()
        .withRoutingKey(eventType.routingKey())
        .build());
emitter.send(message);
```

The static `default-routing-key=notification.send` line has been removed from `application.properties`. Each event is now independently routable by the broker.

---

## Section 2 — P1 Item Validation

---

### ARCH-02 — Repository received a DTO
**Current status:** ✅ RESOLVED

`ListingRepository.search(ListingFilter filter)` — no DTO import. `ListingFilter` is a domain value object in `ai.motoria.listing.domain`.

---

### ARCH-03 — Mapper called domain behavior
**Current status:** ✅ RESOLVED

`ListingMapper` contains only `toResponse()` and `toSummary()`. The `updateEntity()` method is gone. `ListingService.applyUpdate()` calls `listing.updateDetails(...)` directly.

---

### ARCH-05 — Entity exposed public setters
**Current status:** ✅ RESOLVED

`Listing` has no public setter methods. The factory method `Listing.create(...)` is the only external construction path. `initialize()` is now `private`. The `@PrePersist` guard remains as a JPA safety net.

---

### SEC-05 — Raw exception message exposed
**Current status:** ✅ RESOLVED

Fallback logs server-side and returns a static message:
```java
Log.error("Unhandled exception", exception);
return build(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
```

---

### SEC-06 — Missing HSTS header
**Current status:** ✅ RESOLVED

```java
responseContext.getHeaders().add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
```

---

### SEC-07 — Rate limiting not implemented
**Current status:** ✅ RESOLVED with defects — see Section 3, RG-02 and RG-03.

`RateLimitingFilter` is implemented using Redis INCR + EXPIRE with a configurable limit. Two bugs are present.

---

### SEC-08 — Audit log incomplete
**Current status:** ✅ RESOLVED

`AuditInterceptor` now captures user subject, action name, and success/failure outcome.

```java
Log.infov("AUDIT OK | user={0} | action={1}", user, action);
Log.warnv("AUDIT FAIL | user={0} | action={1} | error={2}", user, action, exception.getMessage());
```

`@Audited` now has `@Inherited`. CDI proxy propagation is correct.

---

### SEC-09 — Swagger always enabled
**Current status:** ✅ RESOLVED

```properties
%dev.quarkus.swagger-ui.always-include=true
%test.quarkus.swagger-ui.always-include=true
%prod.quarkus.swagger-ui.enable=false
```

---

### PERF-01 — Unbounded search result
**Current status:** ✅ RESOLVED

`ListingSearchService` normalizes and caps pagination:
```java
int page = Math.max(request.page, 0);
int size = Math.min(Math.max(request.size, 1), 50);
```

`ListingRepository` applies pagination:
```java
return find(query.toString(), parameters)
    .page(filter.page(), filter.size())
    .list();
```

`ListingSearchRequest` has `@QueryParam("page") @DefaultValue("0")` and `@QueryParam("size") @DefaultValue("20")`.

---

### PERF-02 — External call inside `@Transactional`
**Current status:** PARTIALLY RESOLVED — introduced a critical regression. See Section 3, RG-01.

---

### MOD-01 — `AiPriceIntegration` had no interface
**Current status:** ✅ RESOLVED

`AiPriceIntegration` is now a Java interface. `StubAiPriceIntegration` implements it with a clear Javadoc explaining its temporary nature. CDI injects the stub as the sole available implementation.

---

### ARCH-08 — `@Audited` missing `@Inherited`
**Current status:** ✅ RESOLVED

```java
@Inherited
@InterceptorBinding
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {}
```

---

### AP-04 — Empty `ListingEventConsumer`
**Current status:** ✅ RESOLVED — file deleted.

---

## Section 3 — Regressions Introduced by Refactor

> Review-agent criterion: architectural violations must be absent.
> This section identifies new violations created during remediation.

---

### RG-01 — CRITICAL: `@Transactional` self-invocation — `create()` and `update()` have no active transaction

**Files:** `listing/service/ListingService.java`
**Severity:** Critical blocker

The refactor for PERF-02 introduced a CDI self-invocation problem. The pattern used is:

```java
// Public method — @Audited only, no @Transactional
@Audited
public ListingResponse create(CreateListingRequest request) {
    PriceRange priceRange = aiPriceIntegration.recommendPriceRange(request.price());
    UUID sellerId = UUID.fromString(jwt.getSubject());
    return persistListing(request, sellerId, priceRange); // ← internal call
}

// Package-private method — @Transactional declared
@Transactional
ListingResponse persistListing(...) { ... }
```

CDI interceptors (including `@Transactional`) operate exclusively through the CDI proxy. When `create()` calls `persistListing(request, sellerId, priceRange)`, the call goes directly to the method on `this` — it never passes through the proxy. Therefore `@Transactional` on `persistListing()` is silently ignored.

The consequence: `listingRepository.persist(listing)` is called with no active JTA transaction. Panache requires an active transaction for write operations. This will result in a `TransactionRequiredException` at runtime, breaking the `create` and `update` operations entirely.

The same problem applies to `update()` → `applyUpdate()`.

**Root cause:** The remediation plan's TASK 7.3 proposed extracting a non-`@Transactional` public method and calling a `@Transactional` private/package-private helper. This pattern does not work in CDI — it is the classic CDI self-invocation anti-pattern.

**Required fix (no code, plan only):**
Option A — Simplest: restore `@Transactional` to `create()` and `update()` directly. Accept that the AI call occurs inside the transaction. Given that `StubAiPriceIntegration` is currently local and fast, this is safe for now. When `HttpAiPriceIntegration` is introduced, revisit with a proper async flow.

Option B — Correct architecture: extract a separate `@ApplicationScoped ListingPersistenceService` bean containing `@Transactional persistListing()` and `@Transactional applyUpdate()`. `ListingService` injects this bean and calls it through CDI — the proxy is respected on inter-bean calls.

---

### RG-02 — Bug: Rate limiting path exclusion misses leading slash

**File:** `common/security/RateLimitingFilter.java`
**Severity:** Medium bug

```java
String path = requestContext.getUriInfo().getPath();
if (path.startsWith("q/health") || path.startsWith("q/metrics")) {
    return;
}
```

`ContainerRequestContext.getUriInfo().getPath()` returns paths with a leading `/` (e.g., `/q/health`, `/q/metrics`). The string `"q/health"` will never match `/q/health`. Health and metrics endpoints will never be excluded from rate limiting, causing every monitoring probe to consume rate limit quota.

**Required fix:** Change the prefix strings to `"/q/health"` and `"/q/metrics"`.

---

### RG-03 — Bug: All unauthenticated requests share a single rate limit key

**File:** `common/security/RateLimitingFilter.java`
**Severity:** Medium security issue (OWASP A04 — Insecure Design)

```java
String subject = (jwt != null && jwt.getSubject() != null) ? jwt.getSubject() : "anonymous";
String key = "ratelimit:" + subject + ":" + path;
```

All unauthenticated callers share the key `ratelimit:anonymous:/listings`. A single attacker making 100 requests per minute exhausts the quota for all other unauthenticated users on that endpoint — this is effectively a rate-limit-amplified DoS vector.

**Required fix:** For unauthenticated requests, use the client IP address as the key discriminator. The client IP is obtainable from `ContainerRequestContext` via `X-Forwarded-For` header or `requestContext.getUriInfo().getBaseUri().getHost()` (or inject `HttpServerRequest` in Quarkus to get `remoteAddress()`).

---

### RG-04 — Medium: `ConstraintViolationException` message still exposes internals

**File:** `common/exception/GlobalExceptionMapper.java`
**Severity:** Medium (OWASP A09 — Security Logging)

The fallback exception handler was fixed to return a generic message, but the `ConstraintViolationException` branch still exposes the raw violation message:

```java
if (exception instanceof ConstraintViolationException constraintViolationException) {
    return build(Response.Status.BAD_REQUEST, "VALIDATION_ERROR",
                 constraintViolationException.getMessage()); // ← still raw
}
```

`ConstraintViolationException.getMessage()` includes field paths, class names, and constraint descriptors (e.g., `create.request.title: must not be blank`). This reveals internal field structure to API consumers.

**Required fix:** Iterate `constraintViolationException.getConstraintViolations()` to build a sanitized list of `{field, message}` pairs. Return only the constraint violation messages, not the full exception message.

---

## Section 4 — Security Agent Checklist (security-owasp.md)

| OWASP Control | Status | Evidence |
|---|---|---|
| Input validation | ✅ PASS | `@Valid` on all REST DTOs; `@NotBlank`, `@Size`, `@DecimalMin`, `@Min`, `@Max` present |
| Authentication | ✅ PASS | JWT injected; `jwt.getSubject()` used for identity; `@RolesAllowed` on all endpoints |
| Authorization | ⚠️ PARTIAL | Ownership guard implemented; but `ADMIN` bypass not modeled (ADMIN skips `assertOwnership` — this may be intentional but is undocumented) |
| Secure logging | ✅ PASS | Unhandled exceptions logged server-side; generic message returned to client; audit log captures actor |
| XSS protection | ❌ OPEN | No HTML sanitization on `title` or `description` — P2 unresolved |
| Rate limiting | ⚠️ PARTIAL | Implemented but two bugs present (RG-02, RG-03) |
| CSRF protection | ✅ PASS | Delegated to WSO2 API Manager — acceptable for service-type application |
| Secure uploads | N/A | Not in scope for this sprint |

---

## Section 5 — Code Review Skill Checklist (code-review.md)

### Architecture

| Check | Status | Note |
|---|---|---|
| Controllers thin | ✅ PASS | No business logic in `ListingResource` |
| Business logic in services | ✅ PASS | All logic in `ListingService` and `ListingSearchService` |
| Repository = persistence only | ✅ PASS | `ListingRepository` contains only Panache query logic |
| DTO separated from entities | ✅ PASS | Mapper handles conversion; no entity leaves the service layer |
| External integrations isolated | ✅ PASS | `AiPriceIntegration` is an interface; implementation is a named stub |
| Event routing functional | ✅ PASS | `OutgoingRabbitMQMetadata` sets routing key per message |
| Exception hierarchy correct | ✅ PASS | `common` defines base types; `listing` extends them |
| No cross-module import in `common` | ✅ PASS | Zero domain module imports in `common` package |

### Security

Covered in Section 4.

### Performance

| Check | Status | Note |
|---|---|---|
| Pagination enforced | ✅ PASS | Size capped at 50; default page 0, size 20 |
| No unbounded result set | ✅ PASS | `.page(filter.page(), filter.size()).list()` |
| AI call outside transaction | ❌ REGRESSION | Self-invocation breaks `@Transactional` — RG-01 |

### Modularity

| Check | Status | Note |
|---|---|---|
| Integration has interface | ✅ PASS | `AiPriceIntegration` interface + `StubAiPriceIntegration` |
| No singleton anti-pattern | ✅ PASS | All beans `@ApplicationScoped` |
| Event producers use `DomainEventPublisher` | ✅ PASS | `ListingEventProducer` delegates to interface |
| 11 modules are scaffold-only | ❌ OPEN | P2 — no implementation outside `listing` |

---

## Section 6 — Unresolved Items Classification

Items not addressed by the remediation, classified by current priority.

---

### P1 — New blocker (regression)

| ID | Item | File | Why P1 |
|---|---|---|---|
| RG-01 | `@Transactional` self-invocation breaks `create()` and `update()` | `ListingService.java` | Runtime failure — `persist()` throws without active tx |
| RG-02 | Rate limit path exclusion broken (missing `/`) | `RateLimitingFilter.java` | Health probes consume rate limit quota |
| RG-03 | Anonymous rate limit shared key — DoS vector | `RateLimitingFilter.java` | Single attacker blocks all unauthenticated users |
| RG-04 | `ConstraintViolationException.getMessage()` still raw | `GlobalExceptionMapper.java` | Internal field paths exposed to API consumers |

---

### P2 — High priority, pre-release

| ID | Item | File | Reason |
|---|---|---|---|
| SEC-10 | HTML sanitization not implemented | `ListingService.java`, `pom.xml` | OWASP A03 — XSS via stored text fields |
| MOD-02 | `CertificationEventConsumer` not implemented | `certification/event/` | `listing.certification.requested` published with no consumer |
| MOD-03 | `NotificationEventConsumer` not implemented | `notification/event/` | All domain events unprocessed — no user notifications |
| AP-08 | Test coverage — smoke test only | `ListingResourceTest.java` | No domain, state machine, or authorization tests |
| ADMIN-01 | `ADMIN` skips `assertOwnership` — undocumented | `ListingService.java` | May be intentional but no comment or policy document exists |

---

### P3 — Important, next sprint

| ID | Item | File | Reason |
|---|---|---|---|
| MOD-04 | `PromotionEventConsumer` not implemented | `promotion/event/` | `listing.published` triggers no promotion |
| INT-01 | `HttpAiPriceIntegration` not implemented | `listing/integration/` | AI price recommendation is a fixed ±10% formula |
| MOD-05 | `user`, `vehicle`, `partner`, `media`, `ai`, `financing`, `inspection`, `subscription` scaffold only | All modules | Platform non-functional beyond listing |
| PERF-03 | Vehicle catalog has no `@CacheResult` | Future `vehicle/service/` | No Redis caching on reference data |
| TEST-01 | Unit tests for `Listing` domain state machine | `listing/domain/ListingTest.java` | Must cover all state transitions and invariants |
| TEST-02 | `ListingService` unit tests with mocked dependencies | `listing/service/ListingServiceTest.java` | Must cover ownership, events, not-found |

---

### P4 — Technical debt

| ID | Item | Note |
|---|---|---|
| PERF-04 | No index strategy defined for `listing` schema | `modelYear`, `status`, `category`, `price` columns will be used in WHERE clauses — indexes required |
| SEC-11 | `quarkus.hibernate-orm.database.generation=update` in base config | Must be `validate` or `none` in production; schema migration should use Flyway or Liquibase |
| SEC-12 | Rate limit config only in `application.properties` — no per-role differentiation | ADMIN users should have higher or no limit |
| ARCH-09 | `ListingSearchRequest` still uses public fields | JAX-RS `@BeanParam` works with private + getters — minor encapsulation debt |

---

## Section 7 — Re-Review Verdict Summary

### Review Agent Checklist

| Criterion | Result |
|---|---|
| Business logic in controllers | ✅ PASS |
| Missing validation — input DTOs | ✅ PASS |
| Missing validation — ownership | ✅ PASS |
| Architecture violations | ❌ FAIL — RG-01 (self-invocation) |

### Final Verdict

```
STATUS: REJECTED

Reason: RG-01 — @Transactional self-invocation regression.
        create() and update() have no active transaction at runtime.
        persist() will throw TransactionRequiredException.
        This is a critical functional regression introduced during PERF-02 remediation.
```

---

## Section 8 — Next Priority Execution Order

```
IMMEDIATE (resolve before any other work)

  1. RG-01  Fix @Transactional regression in create() and update()
            Option A: restore @Transactional to create() and update() directly
            Option B: extract ListingPersistenceService bean (preferred long-term)

  2. RG-02  Fix path prefix in RateLimitingFilter: "q/health" → "/q/health"
  3. RG-03  Fix anonymous rate limit key: use client IP for unauthenticated callers
  4. RG-04  Fix ConstraintViolationException: return sanitized violation list, not getMessage()

SPRINT 2 (before first deployment)

  5. SEC-10  Add OWASP HTML Sanitizer to pom.xml + sanitize title/description in service
  6. MOD-02  Implement CertificationEventConsumer (consumes listing.certification.requested)
  7. MOD-03  Implement NotificationEventConsumer (consumes all domain events)
  8. AP-08   Write ListingTest (domain state machine) + ListingServiceTest (service unit tests)
  9. AP-08   Expand ListingResourceTest with @TestSecurity role-based integration tests
 10. ADMIN-01 Document or enforce ADMIN bypass policy in assertOwnership or architecture docs

SPRINT 3 (pre-production hardening)

 11. MOD-04  Implement PromotionEventConsumer
 12. INT-01  Implement HttpAiPriceIntegration (when AI module is ready)
 13. SEC-11  Replace database.generation=update with Flyway migration
 14. PERF-03 Add @CacheResult to vehicle catalog service
 15. SEC-12  Add per-role rate limit tiers
 16. TEST-01 Performance test: search endpoint under load with pagination
 17. PERF-04 Define index strategy for listing schema columns

FUTURE SPRINTS

 18–24. Implement scaffold modules in dependency order:
         user → vehicle → partner → media → ai → financing → inspection → subscription
```

---

*Re-Review Report v2 — Motoria.ai Backend*
*Agents: review-agent · security-agent · architect-agent*
*Verdict: REJECTED — resolve RG-01 through RG-04 and resubmit*
