# Motoria.ai — Backend Remediation Plan

```
Authority   : MOTORIA_MASTER_PROMPT.md
Agents      : architect-agent · security-agent · review-agent
Source      : REVIEW_REPORT.md
Target      : Codex execution
Date        : 2026-03-15
```

> This document is an execution plan only.
> No code is written here. Codex must implement each task exactly as described.
> Each task specifies: affected files · transformation required · dependencies · constraints.

---

## Execution Phases

```
PHASE 1 — Config hardening          (no code changes, unblocks deployment)
PHASE 2 — Exception hierarchy       (foundation for all module exception handling)
PHASE 3 — Access control            (JWT ownership — BLOCK on security agent checklist)
PHASE 4 — Domain model refactor     (entity integrity — prerequisite for Phase 5–6)
PHASE 5 — Layer contract fixes      (repository · mapper · service alignment)
PHASE 6 — Security additions        (headers · audit · rate limiting)
PHASE 7 — Integration contracts     (interface definitions · stub naming)
PHASE 8 — Performance fixes         (pagination · transaction boundary)
PHASE 9 — Technical debt            (test coverage · swagger · sanitization)
```

Phases must be executed in order. Tasks within the same phase are independent and can be parallelized by Codex.

---

## PHASE 1 — Configuration Hardening

**Priority:** BLOCK
**Agents:** security-agent (input validation, secure configuration)
**Review findings:** SEC-01, SEC-04, SEC-09

No Java changes. Properties file only.

---

### TASK 1.1 — Externalize all credentials

**File:** `src/main/resources/application.properties`
**Finding:** SEC-01 (OWASP A07 — hardcoded credentials)

**Transformation:**
Replace every hardcoded secret value with an environment variable expression using the Quarkus `${ENV_VAR}` syntax.

| Property key | Replace value with |
|---|---|
| `quarkus.datasource.username` | `${MOTORIA_DB_USER}` |
| `quarkus.datasource.password` | `${MOTORIA_DB_PASSWORD}` |
| `quarkus.datasource.jdbc.url` | `${MOTORIA_DB_URL}` |
| `quarkus.oidc.auth-server-url` | `${MOTORIA_OIDC_URL}` |
| `quarkus.oidc.credentials.secret` | `${MOTORIA_OIDC_SECRET}` |
| `mp.messaging.outgoing.motoria-events-out.host` | `${RABBITMQ_HOST}` |
| `mp.messaging.outgoing.motoria-events-out.username` | `${RABBITMQ_USER}` |
| `mp.messaging.outgoing.motoria-events-out.password` | `${RABBITMQ_PASSWORD}` |

**Constraints:**
- Do not remove any property keys
- Do not change any other property
- Do not create a `.env` file

---

### TASK 1.2 — Restrict CORS to known origins

**File:** `src/main/resources/application.properties`
**Finding:** SEC-04 (OWASP A05 — CORS wildcard)

**Transformation:**
Keep `quarkus.http.cors=true`. Add the following properties immediately after it:

```
quarkus.http.cors.origins=https://app.motoria.ai,https://admin.motoria.ai,https://backoffice.motoria.ai
quarkus.http.cors.methods=GET,POST,PUT,PATCH,DELETE,OPTIONS
quarkus.http.cors.headers=Authorization,Content-Type,Accept
quarkus.http.cors.access-control-allow-credentials=true
quarkus.http.cors.access-control-max-age=3600
```

**Constraints:**
- Do not remove the `quarkus.http.cors=true` line
- Localhost origins must not be added to the base file (only in a dev profile override if needed)

---

### TASK 1.3 — Remove static default RabbitMQ routing key

**File:** `src/main/resources/application.properties`
**Finding:** ARCH-04 (all events sent to `notification.send`)

**Transformation:**
Delete the line:
```
mp.messaging.outgoing.motoria-events-out.default-routing-key=notification.send
```

Do not add any replacement routing key in this file. Routing keys will be set per-message in Phase 2 (TASK 2.1).

**Constraints:**
- Do not change any other RabbitMQ property
- This task must be completed before TASK 2.1 is committed

---

### TASK 1.4 — Restrict Swagger UI to dev profile

**File:** `src/main/resources/application.properties`
**Finding:** SEC-09 (OWASP A05 — Swagger exposed in production)

**Transformation:**
Replace:
```
quarkus.swagger-ui.always-include=true
```
With:
```
%dev.quarkus.swagger-ui.always-include=true
%test.quarkus.swagger-ui.always-include=true
%prod.quarkus.swagger-ui.enable=false
```

---

## PHASE 2 — Exception Hierarchy + Event Routing

**Priority:** BLOCK
**Agents:** architect-agent (module boundaries), review-agent (architecture violations)
**Review findings:** ARCH-01, ARCH-04

These two tasks are independent and can be done in parallel. ARCH-01 must complete before Phase 5 begins.

---

### TASK 2.1 — Define base exception hierarchy in `common`

**New files to create:**
- `src/main/java/ai/motoria/common/exception/NotFoundException.java`
- `src/main/java/ai/motoria/common/exception/InvalidStateException.java`
- `src/main/java/ai/motoria/common/exception/ForbiddenOperationException.java`

**Transformation — `NotFoundException.java`:**
A public class extending `RuntimeException`. Constructor accepts a `String message`. No other methods.

**Transformation — `InvalidStateException.java`:**
A public class extending `RuntimeException`. Constructor accepts a `String message`. No other methods.

**Transformation — `ForbiddenOperationException.java`:**
A public class extending `RuntimeException`. Constructor accepts a `String message`. No other methods.

---

### TASK 2.2 — Update listing exceptions to extend base types

**Files to modify:**
- `src/main/java/ai/motoria/listing/exception/ListingNotFoundException.java`
- `src/main/java/ai/motoria/listing/exception/ListingInvalidStateException.java`

**Transformation — `ListingNotFoundException`:**
Change `extends RuntimeException` to `extends NotFoundException`.
Add import for `ai.motoria.common.exception.NotFoundException`.
Constructor signature and body remain unchanged.

**Transformation — `ListingInvalidStateException`:**
Change `extends RuntimeException` to `extends InvalidStateException`.
Add import for `ai.motoria.common.exception.InvalidStateException`.
Constructor signature and body remain unchanged.

**Dependency:** TASK 2.1 must be completed first.

---

### TASK 2.3 — Remove listing imports from `GlobalExceptionMapper`

**File:** `src/main/java/ai/motoria/common/exception/GlobalExceptionMapper.java`
**Finding:** ARCH-01 (common imports listing module — inverted dependency)

**Transformation:**
1. Remove both import statements:
   - `import ai.motoria.listing.exception.ListingInvalidStateException;`
   - `import ai.motoria.listing.exception.ListingNotFoundException;`
2. Replace the two `instanceof` checks with base type checks:
   - `ListingNotFoundException` → `NotFoundException`
   - `ListingInvalidStateException` → `InvalidStateException`
3. Add imports for:
   - `ai.motoria.common.exception.NotFoundException`
   - `ai.motoria.common.exception.InvalidStateException`
   - `ai.motoria.common.exception.ForbiddenOperationException`
4. Add a new `instanceof ForbiddenOperationException` branch returning HTTP 403 with code `FORBIDDEN`.
5. For the fallback `Exception` branch: add a server-side log call using `Log.error("Unhandled exception", exception)` before building the response. Return a static message string — never `exception.getMessage()`.

**Dependency:** TASK 2.2 must be completed first.

---

### TASK 2.4 — Fix AMQP routing key per message in `RabbitMqEventPublisher`

**File:** `src/main/java/ai/motoria/common/event/RabbitMqEventPublisher.java`
**Finding:** ARCH-04 (all events share one static routing key)

**Transformation:**
1. Change the `emitter.send(String)` call to `emitter.send(Message<String>)`.
2. Build a `Message<String>` that wraps the serialized JSON string.
3. Add `OutgoingRabbitMQMetadata` to the message with the routing key set to `eventType.routingKey()`.
4. Add the necessary import for `OutgoingRabbitMQMetadata` from `io.smallrye.reactive.messaging.rabbitmq`.
5. Add the necessary import for `org.eclipse.microprofile.reactive.messaging.Message`.
6. The rest of the method — serialization, exception wrapping — remains unchanged.

**Constraints:**
- Do not change the `DomainEventPublisher` interface signature
- Do not change `DomainEventEnvelope`
- Do not change `EventType`
- TASK 1.3 must be completed first (static routing key removed from properties)

---

## PHASE 3 — Access Control

**Priority:** BLOCK
**Agents:** security-agent (input validation), architect-agent (business logic in services)
**Review findings:** SEC-02, SEC-03

TASK 3.1 must complete before TASK 3.2. TASK 3.2 must complete before TASK 3.3.

---

### TASK 3.1 — Remove `sellerId` from `CreateListingRequest`

**File:** `src/main/java/ai/motoria/listing/dto/CreateListingRequest.java`
**Finding:** SEC-02 (OWASP A01 — BOLA via client-supplied sellerId)

**Transformation:**
1. Remove the `@NotNull UUID sellerId` field from the record.
2. Remove the `import java.util.UUID` statement if `UUID` is no longer used in the record (check: `vehicleSpecId` still uses UUID, so keep the import).
3. Remove the import for `jakarta.validation.constraints.NotNull` only if no other field uses it — it is still used by other fields, so keep it.

**Constraints:**
- Do not change any other field
- Do not add any replacement field
- This change will cause a compilation error in `ListingService.create()` that is resolved in TASK 3.2

---

### TASK 3.2 — Extract `sellerId` from JWT in `ListingService`

**File:** `src/main/java/ai/motoria/listing/service/ListingService.java`
**Finding:** SEC-02 (sellerId must come from authenticated token)

**Transformation:**
1. Add a new field injection: `@Inject JsonWebToken jwt;`
2. Add import for `org.eclipse.microprofile.jwt.JsonWebToken`.
3. In the `create()` method:
   - After `listingMapper.toEntity(request)`, extract the seller ID: `UUID sellerId = UUID.fromString(jwt.getSubject());`
   - Call `listing.setSellerId(sellerId)` using the existing public setter (the setter will be removed in Phase 4, but must remain available until then).
   - Remove any reference to `request.sellerId()` (which no longer exists after TASK 3.1).
4. Add import for `java.util.UUID` if not already present.

**Dependency:** TASK 3.1 must be completed first.

---

### TASK 3.3 — Add ownership guard to all mutating operations

**File:** `src/main/java/ai/motoria/listing/service/ListingService.java`
**Finding:** SEC-03 (OWASP A01 — no ownership check on mutations)

**Transformation:**
1. Add a new private method `assertOwnership(Listing listing)`:
   - Extract `UUID callerId = UUID.fromString(jwt.getSubject())`
   - If `!listing.getSellerId().equals(callerId)`, throw `new ForbiddenOperationException("Access denied to listing " + listing.getId())`
2. Add import for `ai.motoria.common.exception.ForbiddenOperationException`.
3. Call `assertOwnership(listing)` immediately after the `findById()` call in each of these methods:
   - `update()`
   - `submitForReview()`
   - `markSold()`
   - `requestCertification()`
4. Do NOT call `assertOwnership` in `publish()` — publishing is a BACKOFFICE/ADMIN action, not a seller action. The `@RolesAllowed` already restricts it.
5. Do NOT call `assertOwnership` in `getById()` — reads are open to all authorized roles.

**Dependency:** TASK 3.2 must be completed first (JWT field already injected).

---

## PHASE 4 — Domain Model Refactor

**Priority:** P1
**Agents:** architect-agent (DTO separated from entities, no anemic domain)
**Review findings:** ARCH-05, ARCH-06, AP-01, AP-02

TASK 4.1 must complete before TASK 4.2. Both must complete before Phase 5.

---

### TASK 4.1 — Add factory method to `Listing` entity

**File:** `src/main/java/ai/motoria/listing/domain/Listing.java`
**Finding:** ARCH-05 (entity has public setters bypassing invariants), AP-02 (dual init path)

**Transformation:**
1. Add a `public static Listing create(UUID sellerId, UUID vehicleSpecId, ListingCategory category, String title, String description, BigDecimal price, Integer modelYear, Integer mileage)` static factory method.
2. Inside the factory method:
   - Instantiate a new `Listing` using the no-arg constructor (which must remain for JPA).
   - Assign all fields directly (accessing private fields from within the same class is valid in Java).
   - Set `id = UUID.randomUUID()`.
   - Set `status = ListingStatus.DRAFT`.
   - Set `createdAt = Instant.now()`.
   - Set `updatedAt = createdAt`.
3. Change `initialize()` visibility from `public` to `private`.
4. The `@PrePersist` method `prePersist()` and its `if (id == null)` guard remain unchanged as a safety net.

**Constraints:**
- Do not remove the no-arg constructor (required by JPA)
- Do not remove `@PrePersist`
- Do not remove getters

---

### TASK 4.2 — Remove public setters from `Listing` entity

**File:** `src/main/java/ai/motoria/listing/domain/Listing.java`
**Finding:** AP-01 (public setters bypass domain invariants)

**Transformation:**
Remove all public setter methods:
- `setTitle()`
- `setDescription()`
- `setCategory()`
- `setSellerId()`
- `setVehicleSpecId()`
- `setPrice()`
- `setModelYear()`
- `setMileage()`

**Dependency:** TASK 4.1 must be completed first (factory method provides alternative to setters).
TASK 5.1 and TASK 5.3 must be completed before this task (mapper and service must no longer call setters).

**Constraints:**
- Do not remove domain behavior methods (`updateDetails`, `submitForReview`, `publish`, `markSold`, `applyRecommendedPriceRange`)
- Do not remove getters
- Do not remove `@PrePersist`

---

### TASK 4.3 — Create `ListingFilter` domain value object

**New file:** `src/main/java/ai/motoria/listing/domain/ListingFilter.java`
**Finding:** ARCH-02 (repository receives DTO)

**Transformation:**
Create a Java record named `ListingFilter` in package `ai.motoria.listing.domain` with the following fields:
- `ListingStatus status` (nullable)
- `ListingCategory category` (nullable)
- `BigDecimal minPrice` (nullable)
- `BigDecimal maxPrice` (nullable)
- `Integer modelYear` (nullable)
- `int page`
- `int size`

No validation annotations. No JPA annotations. No business logic.

---

## PHASE 5 — Layer Contract Fixes

**Priority:** P1
**Agents:** architect-agent (repository = persistence only; mapper = data conversion only)
**Review findings:** ARCH-02, ARCH-03, PERF-01

TASK 5.1, 5.2, and 5.3 are independent. TASK 5.4 depends on 5.1 and 5.3.

---

### TASK 5.1 — Remove domain behavior from `ListingMapper`

**File:** `src/main/java/ai/motoria/listing/mapper/ListingMapper.java`
**Finding:** ARCH-03 (mapper calls domain behavior)

**Transformation:**
1. Delete the `default void updateEntity(UpdateListingRequest request, Listing listing)` method entirely.
2. Remove the `import` for `UpdateListingRequest` only if it is no longer used — it is still used in no mapping method, so remove it.
3. The three remaining mapper methods (`toEntity`, `toResponse`, `toSummary`) remain unchanged.

**Constraints:**
- Do not touch any `@Mapping` annotation
- Do not change the `@Mapper(componentModel = "cdi")` annotation

---

### TASK 5.2 — Update `ListingMapper.toEntity` to use factory method

**File:** `src/main/java/ai/motoria/listing/mapper/ListingMapper.java`
**Finding:** AP-01 (setters on entity)

**Transformation:**
The current `Listing toEntity(CreateListingRequest request)` MapStruct mapping will attempt to use setters that will be removed in TASK 4.2. Replace it with a `default` method that delegates to `Listing.create(...)`:

```
default Listing toEntity(CreateListingRequest request) {
    // delegate to Listing.create() — do not call setters
    // sellerId is NOT available from request after TASK 3.1; leave it null here.
    // ListingService.create() will set sellerId from JWT after calling this mapper.
}
```

Codex must implement the delegation using all available fields from `CreateListingRequest` (category, vehicleSpecId, title, description, price, modelYear, mileage). `sellerId` is passed as `null` here — the service sets it immediately after via a dedicated internal method or directly during the factory call refactor.

**Alternative approach (preferred):** Remove `toEntity(CreateListingRequest)` from the mapper entirely. In `ListingService.create()`, call `Listing.create(sellerId, request.vehicleSpecId(), ...)` directly using the factory method. This keeps the mapper stateless and avoids the null sellerId issue.

**Dependency:** TASK 3.1 (sellerId removed from DTO), TASK 4.1 (factory method exists).

---

### TASK 5.3 — Update `ListingService.update()` to call domain method directly

**File:** `src/main/java/ai/motoria/listing/service/ListingService.java`
**Finding:** ARCH-03 (mapper was calling domain method — must move to service)

**Transformation:**
In the `update()` method, replace the call:
```
listingMapper.updateEntity(request, listing);
```
With a direct call to the domain method:
```
listing.updateDetails(request.title(), request.description(), request.price(), request.modelYear(), request.mileage());
```

**Dependency:** TASK 5.1 must be completed first (method removed from mapper).

---

### TASK 5.4 — Refactor `ListingRepository.search()` to accept `ListingFilter`

**File:** `src/main/java/ai/motoria/listing/repository/ListingRepository.java`
**Finding:** ARCH-02 (repository receives DTO), PERF-01 (unbounded result)

**Transformation:**
1. Change the method signature from `search(ListingSearchRequest request)` to `search(ListingFilter filter)`.
2. Replace all `request.` field accesses with `filter.` field accesses.
3. Replace `.list()` at the end with `.page(filter.page(), filter.size()).list()`.
4. Remove the import for `ai.motoria.listing.dto.ListingSearchRequest`.
5. Add the import for `ai.motoria.listing.domain.ListingFilter`.

**Dependency:** TASK 4.3 must be completed first (`ListingFilter` exists).

---

### TASK 5.5 — Refactor `ListingSearchService` to map DTO to `ListingFilter`

**File:** `src/main/java/ai/motoria/listing/service/ListingSearchService.java`
**Finding:** ARCH-02 follow-on

**Transformation:**
In the `search(ListingSearchRequest request)` method, before calling `listingRepository.search(...)`, construct a `ListingFilter` from the request fields:
- Map each field directly (status, category, minPrice, maxPrice, modelYear).
- For `page` and `size`: read from `request.page` and `request.size` (added in TASK 5.6). Apply a default of `0` for page and `20` for size if null. Cap size at `50`.
- Pass the constructed `ListingFilter` to `listingRepository.search(filter)`.

**Dependency:** TASK 4.3 (ListingFilter), TASK 5.4 (repository signature changed), TASK 5.6 (page/size on DTO).

---

### TASK 5.6 — Add pagination fields to `ListingSearchRequest`

**File:** `src/main/java/ai/motoria/listing/dto/ListingSearchRequest.java`
**Finding:** PERF-01 (no pagination)

**Transformation:**
1. Add two new fields to the class:
   - `@QueryParam("page") @DefaultValue("0") public int page;`
   - `@QueryParam("size") @DefaultValue("20") public int size;`
2. Add import for `jakarta.ws.rs.DefaultValue`.

**Constraints:**
- Do not change existing fields
- Do not change field visibility (must remain public for `@BeanParam` binding)

---

### TASK 5.7 — Delete `ListingEventConsumer` empty class

**File:** `src/main/java/ai/motoria/listing/event/ListingEventConsumer.java`
**Finding:** AP-04 (empty bean implies behavior that does not exist)

**Transformation:**
Delete the file entirely.

**Constraints:**
- Ensure no other class imports or references `ListingEventConsumer` before deleting.

---

### TASK 5.8 — Update `ListingService.create()` to use factory method

**File:** `src/main/java/ai/motoria/listing/service/ListingService.java`
**Finding:** AP-01, AP-02 (dual init path, public initialize call)

**Transformation:**
In `create()`:
1. Replace `listingMapper.toEntity(request)` + `listing.initialize()` with a single call to `Listing.create(sellerId, request.vehicleSpecId(), request.category(), request.title(), request.description(), request.price(), request.modelYear(), request.mileage())`.
2. Remove the `listing.initialize()` call entirely.
3. Add import for `ai.motoria.listing.domain.Listing` if not already imported (it is, via the return type).

**Dependency:** TASK 3.2 (sellerId from JWT), TASK 4.1 (factory method), TASK 5.1 (toEntity removed from mapper or replaced).

---

## PHASE 6 — Security Additions

**Priority:** P1
**Agents:** security-agent (rate limiting, XSS, audit)
**Review findings:** SEC-05, SEC-06, SEC-07, SEC-08

All tasks in this phase are independent and can be parallelized.

---

### TASK 6.1 — Add `Strict-Transport-Security` to `SecurityHeadersFilter`

**File:** `src/main/java/ai/motoria/common/security/SecurityHeadersFilter.java`
**Finding:** SEC-06 (OWASP A05 — missing HSTS)

**Transformation:**
In the `filter()` method, add one line after the existing headers:
```
responseContext.getHeaders().add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
```

**Constraints:**
- Do not remove or modify any existing header
- Do not add any other headers in this task

---

### TASK 6.2 — Improve `AuditInterceptor` to capture actor and outcome

**File:** `src/main/java/ai/motoria/common/audit/AuditInterceptor.java`
**Finding:** SEC-08 (OWASP A09 — audit log missing actor and outcome)

**Transformation:**
1. Inject `@Inject JsonWebToken jwt` as a field.
2. Add import for `org.eclipse.microprofile.jwt.JsonWebToken`.
3. In `logAudit(InvocationContext context)`:
   - Extract user subject: `String user = (jwt != null && jwt.getSubject() != null) ? jwt.getSubject() : "anonymous"`.
   - Build action string: `ClassName.methodName`.
   - Wrap `context.proceed()` in a try/catch.
   - On success: log `AUDIT OK | user=... | action=...` using `Log.infov`.
   - On exception: log `AUDIT FAIL | user=... | action=... | error=...` using `Log.warnv`, then rethrow the exception unchanged.

**Constraints:**
- Do not change the `@Audited` annotation itself in this task (done in TASK 6.3)
- Do not suppress or swallow exceptions

---

### TASK 6.3 — Add `@Inherited` to `@Audited` annotation

**File:** `src/main/java/ai/motoria/common/audit/Audited.java`
**Finding:** ARCH-08 (interceptor binding not propagated to proxies)

**Transformation:**
Add `@Inherited` annotation directly above `@InterceptorBinding`.
Add import for `java.lang.annotation.Inherited`.

---

### TASK 6.4 — Implement `RateLimitingFilter`

**New file:** `src/main/java/ai/motoria/common/security/RateLimitingFilter.java`
**Finding:** SEC-07 (OWASP A04 — rate limiting not implemented)

**Transformation:**
Create a JAX-RS `ContainerRequestFilter` annotated with `@Provider`. The filter must:
1. Inject the Quarkus Redis client (`@Inject io.quarkus.redis.datasource.ReactiveRedisDataSource` or the blocking variant).
2. Inject `JsonWebToken jwt`.
3. In the `filter(ContainerRequestContext)` method:
   - Build a Redis key from: `"ratelimit:" + jwt.getSubject() + ":" + requestContext.getUriInfo().getPath()`.
   - Use Redis INCR to increment the counter. If the result is 1, set an EXPIRE of 60 seconds (sliding window per minute).
   - If the counter exceeds the configured limit (default: 100 requests per minute), abort the request with HTTP 429 and a JSON body `{"code":"RATE_LIMIT_EXCEEDED","message":"Too many requests"}`.
4. Add the rate limit as a configurable property: `motoria.rate-limit.requests-per-minute=100` read via `@ConfigProperty`.

**Constraints:**
- Do not apply rate limiting to health check endpoints (`/q/health`, `/q/metrics`)
- Do not hardcode the limit value

---

## PHASE 7 — Integration Contracts

**Priority:** P1
**Agents:** architect-agent (external integrations must be isolated)
**Review findings:** MOD-02, AP-06

Tasks 7.1 and 7.2 are sequential. TASK 7.3 is independent.

---

### TASK 7.1 — Define `AiPriceIntegration` as an interface

**File:** `src/main/java/ai/motoria/listing/integration/AiPriceIntegration.java`
**Finding:** MOD-02 (no interface — not swappable)

**Transformation:**
Convert the concrete class into a Java interface:
1. Change `public class AiPriceIntegration` to `public interface AiPriceIntegration`.
2. Remove `@ApplicationScoped`.
3. Remove the method body from `recommendPriceRange(BigDecimal basePrice)` — it becomes an abstract method declaration.
4. Remove all imports no longer needed (BigDecimal stays, ApplicationScoped removed).

**Dependency:** Must complete before TASK 7.2.

---

### TASK 7.2 — Rename stub implementation

**New file:** `src/main/java/ai/motoria/listing/integration/StubAiPriceIntegration.java`

**Transformation:**
Create a new class `StubAiPriceIntegration` that:
1. Is annotated `@ApplicationScoped`.
2. Implements `AiPriceIntegration`.
3. Contains the current `recommendPriceRange()` method body (the ±10% formula).
4. Has a class-level Javadoc comment: `/** Stub implementation. Replace with HttpAiPriceIntegration when AI module is available. */`

`ListingService` field type remains `AiPriceIntegration` (the interface). CDI will inject `StubAiPriceIntegration` as the sole implementation.

**Dependency:** TASK 7.1 must be completed first.

**Constraints:**
- Do not modify `ListingService` injection point — it already injects `AiPriceIntegration`
- Do not add `@Named` qualifier unless a second implementation exists

---

### TASK 7.3 — Move external AI call outside `@Transactional`

**File:** `src/main/java/ai/motoria/listing/service/ListingService.java`
**Finding:** PERF-02 (external call inside open transaction holds DB connection)

**Transformation:**
In both `create()` and `update()`:
1. Extract the `aiPriceIntegration.recommendPriceRange(...)` call to before the `@Transactional` boundary.
2. Since `create()` and `update()` themselves are `@Transactional`, introduce a private non-transactional helper or restructure so the AI call precedes transaction opening.

**Recommended restructure for `create()`:**
- Extract a `@Transactional` private method `persistListing(CreateListingRequest request, PriceRange priceRange)`.
- In the public `create()` method (not `@Transactional`): call AI first, then call `persistListing()`.

**Recommended restructure for `update()`:**
- Extract a `@Transactional` private method `applyUpdate(UUID listingId, UpdateListingRequest request, PriceRange priceRange)`.
- In the public `update()` method (not `@Transactional`): call AI first, then call `applyUpdate()`.

**Constraints:**
- The `@Audited` annotation must remain on the public method, not the private helper
- Do not move `@Transactional` to the class level

---

## PHASE 8 — Performance

**Priority:** P1
**Agents:** architect-agent (scalability), review-agent (missing validation)
**Review findings:** PERF-01

TASK 8.1 is already covered by TASK 5.4 (pagination in repository) and TASK 5.6 (page/size on DTO). No additional tasks required in this phase if Phase 5 is completed.

**Verification checklist for Codex after Phase 5:**
- [ ] `ListingSearchRequest` has `page` and `size` with `@DefaultValue`
- [ ] `ListingFilter` has `page` and `size` as `int` fields
- [ ] `ListingRepository.search()` calls `.page(filter.page(), filter.size()).list()`
- [ ] `ListingSearchService` caps `size` at 50 before constructing `ListingFilter`

---

## PHASE 9 — Technical Debt

**Priority:** P2
**Agents:** security-agent (XSS), review-agent (missing validation)
**Review findings:** SEC-10, AP-08

---

### TASK 9.1 — Add HTML sanitization to text fields in `ListingService`

**File:** `src/main/java/ai/motoria/listing/service/ListingService.java`
**Finding:** SEC-10 (OWASP A03 — XSS via unsanitized text fields)

**Transformation:**
1. Add the OWASP Java HTML Sanitizer dependency to `pom.xml`:
   - `groupId: com.googlecode.owasp-java-html-sanitizer`
   - `artifactId: owasp-java-html-sanitizer`
   - Use the latest stable version.
2. In `ListingService`, create a private helper `sanitize(String input)` that applies `PolicyFactory Sanitizers.FORMATTING` (strips all HTML) and returns the clean string.
3. In `create()`: sanitize `title` and `description` before passing to `Listing.create()`.
4. In `update()`: sanitize `title` and `description` before calling `listing.updateDetails()`.

**Constraints:**
- Do not add sanitization to DTOs or the mapper
- Do not apply sanitization to UUID, enum, numeric, or date fields

---

### TASK 9.2 — Write domain unit tests for `listing` module

**New files:**
- `src/test/java/ai/motoria/listing/domain/ListingTest.java`
- `src/test/java/ai/motoria/listing/service/ListingServiceTest.java`

**Finding:** AP-08 (one smoke test — zero domain coverage)

**`ListingTest.java` must cover:**
- `create()` factory method produces a listing in `DRAFT` status
- `submitForReview()` transitions from `DRAFT` to `PENDING_REVIEW`
- `submitForReview()` throws when status is not `DRAFT`
- `publish()` transitions from `PENDING_REVIEW` to `PUBLISHED`
- `publish()` throws when status is not `PENDING_REVIEW`
- `markSold()` transitions from `PUBLISHED` to `SOLD`
- `markSold()` throws when status is not `PUBLISHED`
- `updateDetails()` updates fields and `updatedAt`

**`ListingServiceTest.java` must cover (using Mockito or QuarkusMock):**
- `create()` persists listing and publishes `LISTING_CREATED` event
- `update()` on a non-owned listing throws `ForbiddenOperationException`
- `markSold()` on a non-owned listing throws `ForbiddenOperationException`
- `getById()` on a non-existent ID throws `ListingNotFoundException`
- `publish()` transitions status and publishes `LISTING_PUBLISHED` event

**Constraints:**
- Use JUnit 5 (`@Test`)
- Use Mockito for service tests (do not use `@QuarkusTest` for unit tests — reserve `@QuarkusTest` for integration tests)
- Do not test infrastructure (RabbitMQ, Redis) in unit tests

---

### TASK 9.3 — Write integration test for `ListingResource`

**File:** `src/test/java/ai/motoria/listing/rest/ListingResourceTest.java`
**Finding:** AP-08

**Transformation:**
Replace the single smoke test with the following `@QuarkusTest` test methods:
- `POST /listings` with valid body and `SELLER` role returns 201
- `POST /listings` with missing required fields returns 400
- `POST /listings` with `BUYER` role returns 403
- `GET /listings/{id}` for a non-existent ID returns 404
- `PATCH /listings/{id}/mark-sold` by non-owner returns 403

Use `@TestSecurity` annotation from `quarkus-test-security` to inject roles per test. Mock the repository and event producer using `@InjectMock`.

---

## File Change Matrix

| File | Phase | Change Type | Depends On |
|---|---|---|---|
| `application.properties` | 1 | Modify | — |
| `common/event/RabbitMqEventPublisher.java` | 2 | Modify | TASK 1.3 |
| `common/exception/NotFoundException.java` | 2 | **Create** | — |
| `common/exception/InvalidStateException.java` | 2 | **Create** | — |
| `common/exception/ForbiddenOperationException.java` | 2 | **Create** | — |
| `common/exception/GlobalExceptionMapper.java` | 2 | Modify | TASK 2.2 |
| `listing/exception/ListingNotFoundException.java` | 2 | Modify | TASK 2.1 |
| `listing/exception/ListingInvalidStateException.java` | 2 | Modify | TASK 2.1 |
| `listing/dto/CreateListingRequest.java` | 3 | Modify | — |
| `listing/service/ListingService.java` | 3,5,7 | Modify | TASK 3.1, 4.1, 5.1 |
| `listing/domain/Listing.java` | 4 | Modify | — |
| `listing/domain/ListingFilter.java` | 4 | **Create** | — |
| `listing/mapper/ListingMapper.java` | 5 | Modify | TASK 4.1 |
| `listing/repository/ListingRepository.java` | 5 | Modify | TASK 4.3 |
| `listing/service/ListingSearchService.java` | 5 | Modify | TASK 4.3, 5.4 |
| `listing/dto/ListingSearchRequest.java` | 5 | Modify | — |
| `listing/event/ListingEventConsumer.java` | 5 | **Delete** | — |
| `common/security/SecurityHeadersFilter.java` | 6 | Modify | — |
| `common/audit/AuditInterceptor.java` | 6 | Modify | — |
| `common/audit/Audited.java` | 6 | Modify | — |
| `common/security/RateLimitingFilter.java` | 6 | **Create** | — |
| `listing/integration/AiPriceIntegration.java` | 7 | Modify (→ interface) | — |
| `listing/integration/StubAiPriceIntegration.java` | 7 | **Create** | TASK 7.1 |
| `listing/domain/ListingTest.java` | 9 | **Create** | Phase 4 |
| `listing/service/ListingServiceTest.java` | 9 | **Create** | Phase 3,4,5 |
| `listing/rest/ListingResourceTest.java` | 9 | Replace | Phase 3,4,5 |
| `pom.xml` | 9 | Modify (add sanitizer dep) | — |

**Summary:**
- Files to **create**: 8
- Files to **modify**: 16
- Files to **delete**: 1

---

## Codex Execution Constraints

```
1. Implement phases in order (1 → 9). Do not skip phases.
2. Within each phase, tasks marked as independent can run in parallel.
3. After each phase, verify the project compiles before proceeding.
4. Do not implement business logic not described in this plan.
5. Do not create files not listed in the File Change Matrix.
6. Do not modify files outside the scope of each task.
7. Do not add dependencies not explicitly listed in this plan.
8. The architect-agent rules apply to every file touched:
   - Controllers must remain thin
   - Business logic only in services
   - DTO never exposed from repository
9. The security-agent checklist must pass after Phase 6:
   - Input validation: @Valid on all REST DTOs
   - XSS protection: sanitization in service
   - Rate limiting: RateLimitingFilter active
   - Secure uploads: not in scope for this sprint
   - CSRF protection: enforced at WSO2 API Manager layer
10. The review-agent will re-review after Phase 9. Expected verdict: APPROVED.
```

---

## Acceptance Criteria (Re-review Checklist)

After all phases complete, the review agent must find:

| Check | Expected |
|---|---|
| Business logic in controllers | None |
| Missing validation | None — ownership guard + JWT extraction + pagination |
| Architecture violations | None — base exceptions, DTO-free repo, pure mapper |
| OWASP A01 Broken Access Control | Resolved — JWT sellerId, ownership guard |
| OWASP A03 Injection | Resolved — HTML sanitization |
| OWASP A04 Insecure Design | Resolved — rate limiting implemented |
| OWASP A05 Misconfiguration | Resolved — CORS, HSTS, Swagger restricted |
| OWASP A07 Misconfiguration | Resolved — no hardcoded credentials |
| OWASP A09 Logging | Resolved — audit log captures actor and outcome |
| Performance — unbounded search | Resolved — pagination enforced |
| Performance — tx + external call | Resolved — AI call outside transaction |
| Anti-patterns | Resolved — setters removed, empty beans deleted, interface on integration |
| Test coverage | Resolved — domain + service + REST tests |

---

*Remediation Plan — Motoria.ai Backend v1.0-SNAPSHOT*
*Prepared by: architect-agent · security-agent · review-agent*
*Ready for: Codex execution*
