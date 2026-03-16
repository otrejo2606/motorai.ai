# Motoria.ai — Backend Review Report V4 (Sprint 2B)

```
Agent   : review-agent.md · architect-agent.md
Skills  : code-review.md · security-owasp.md
Prompt  : review-code.md
Source  : REVIEW_REPORT_V3.md (previous)
Date    : 2026-03-15
Scope   : Sprint 2B — certification module, notification module,
          event consumers, common event contracts,
          RabbitMQ channel configuration, test suite
```

---

## Overall Verdict

```
STATUS: APPROVED
```

Sprint 2B is architecturally sound. Module isolation is maintained. Event normalization is correct. Security controls are intact. Tests cover critical paths including RBAC, ownership, and sanitization. No new BLOCK-level issues introduced.

Seven items from the V3 unresolved list are resolved in this sprint. Six non-blocking observations are carried forward or newly introduced. Full breakdown below.

---

## Section 1 — V3 Unresolved Items Resolution Status

Items that were scheduled for Sprint 2B:

| Item | Description | Status |
|---|---|---|
| SEC-10 | HTML sanitization on title/description | ✅ RESOLVED |
| TEST-01 | `ListingTest.java` — domain state machine | ✅ RESOLVED |
| TEST-02 | `ListingServiceTest.java` — service unit tests | ✅ RESOLVED |
| TEST-03 | `ListingResourceTest.java` — resource integration tests | ✅ RESOLVED |
| MOD-02 | `NotificationEventConsumer` | ✅ RESOLVED |
| MOD-03 | `CertificationEventConsumer` | ✅ RESOLVED |
| OB-02 | `ValidationErrorDetail` leaf field extraction | ✅ RESOLVED |

Items deferred to later sprints (unchanged):

| Item | Status |
|---|---|
| MOD-04 PromotionEventConsumer | ⏳ P2 — not in this sprint |
| OB-04 Transactional outbox | ⏳ P3 — pre-production required |
| SEC-11 Flyway migration | ⏳ P3 |
| OB-03 assertOwnership duplication | ⏳ P3 |
| OB-01 X-Forwarded-For topology | ⏳ P3 |
| INT-01 HttpAiPriceIntegration | ⏳ when AI module ready |

---

## Section 2 — Certification Module Review

### PASS — Module Isolation

`CertificationEventConsumer` is declared in `ai.motoria.certification.event`. It imports only:
- `ai.motoria.common.event.DomainEventEnvelope` — correct use of shared kernel
- `ai.motoria.certification.dto.CertificationRequestedCommand` — module-local command
- `ai.motoria.certification.service.CertificationService` — module-local service

Zero imports from `ai.motoria.listing`. Module boundary is enforced. ✓

### PASS — Event Normalization

The raw `DomainEventEnvelope` is consumed and immediately normalized into `CertificationRequestedCommand`:

```java
certificationService.handleCertificationRequested(new CertificationRequestedCommand(
    envelope.correlationId(), envelope.sourceModule(),
    payload.listingId(), payload.status()));
```

`CertificationService` receives a typed command, not the raw envelope. No RabbitMQ-specific types leak into the service layer. ✓

### PASS — Channel Binding Correctness

```properties
mp.messaging.incoming.listing-certification-requested-in.exchange.name=motoria.events
mp.messaging.incoming.listing-certification-requested-in.queue.name=certification.listing-certification-requested
mp.messaging.incoming.listing-certification-requested-in.routing-keys=listing.certification.requested
```

Dedicated queue per consumer. Routing key is specific. The certification module only receives `listing.certification.requested` events via AMQP binding. ✓

### OBSERVATION — No eventType Guard in Consumer Code (Minor)

**File:** `CertificationEventConsumer.java`
**Severity:** Minor — architecturally defended at transport layer

`onCertificationRequested()` does not validate `envelope.eventType()` against the expected value before dispatching. It relies entirely on the AMQP routing key binding to filter messages.

This is architecturally acceptable: the dedicated queue `certification.listing-certification-requested` is bound only to `listing.certification.requested`, so no other event type reaches this consumer at runtime.

However, in testing or future refactors (e.g., if a test sends raw JSON without the AMQP layer), unexpected event types could pass through silently.

**Recommendation for P3:** Add a guard:
```java
if (!"listing.certification.requested".equals(envelope.eventType())) {
    Log.warnf("Unexpected event type %s on certification channel", envelope.eventType());
    return;
}
```

### OBSERVATION — `CertificationRequestedPayload.status` is `String`, not `ListingStatus`

**File:** `CertificationEventConsumer.java:99–102` (private record)
**Severity:** Minor — type safety gap at event boundary

The local `CertificationRequestedPayload` record declares `status` as `String`. If an invalid status string arrives (e.g., `"UNKNOWN"` or a typo), it passes through to `CertificationService` without any validation. A future service implementation that calls `ListingStatus.valueOf(status)` would throw `IllegalArgumentException` at that point rather than at the consumer boundary.

**Recommendation for P2:** Declare `status` as `ListingStatus` enum directly in the payload record. Jackson will deserialize and validate in one step, with a clear error at the correct boundary:
```java
private record CertificationRequestedPayload(UUID listingId, ListingStatus status) {}
```

### OBSERVATION — No DLQ for `listing-certification-requested-in` Channel

**File:** `application.properties`
**Severity:** Medium operational risk — not a blocker for development

Both `readEnvelope()` in `CertificationEventConsumer` and `NotificationEventConsumer` wrap deserialization failure in `IllegalArgumentException`:

```java
throw new IllegalArgumentException("Unable to deserialize notification event", exception);
```

When this exception propagates out of an `@Blocking` consumer, SmallRye Reactive Messaging marks the message as not-acknowledged. With the default RabbitMQ connector configuration (no DLQ), the broker will redeliver the message indefinitely. A single malformed message can stall the consumer thread indefinitely.

**Affected channels:** `listing-certification-requested-in`, `listing-notification-events-in`

**Recommendation for P2 (before any consumer deployed to production):**

Option A — Add DLQ configuration:
```properties
mp.messaging.incoming.listing-certification-requested-in.failure-strategy=dead-letter-queue
mp.messaging.incoming.listing-certification-requested-in.dead-letter-queue.exchange.name=motoria.events.dlq
mp.messaging.incoming.listing-certification-requested-in.dead-letter-queue.routing-key=listing.certification.requested.dlq
```

Option B — Catch and log at consumer level to nack immediately:
```java
try {
    // ...
} catch (IllegalArgumentException e) {
    Log.errorf(e, "Malformed event — discarding");
    // allow message to be acked (discarded) rather than retried
}
```

Option A is preferred for observability.

---

## Section 3 — Notification Module Review

### PASS — Module Isolation

`NotificationEventConsumer` is declared in `ai.motoria.notification.event`. Imports:
- `ai.motoria.common.event.DomainEventEnvelope` — shared kernel ✓
- `ai.motoria.common.event.EventType` — shared kernel ✓
- `ai.motoria.notification.dto.ListingNotificationCommand` — module-local ✓
- `ai.motoria.notification.service.NotificationService` — module-local ✓

Zero imports from `ai.motoria.listing`. Module boundary is enforced. ✓

### PASS — Multi-Event Routing via `EventType`

```java
EventType eventType = EventType.fromRoutingKey(envelope.eventType()).orElse(null);

if (eventType == null) {
    Log.warnf("Ignoring unsupported notification event type %s", envelope.eventType());
    return;
}

ListingNotificationCommand command = switch (eventType) {
    case LISTING_CREATED -> buildCreatedCommand(envelope, eventType);
    case LISTING_PUBLISHED, LISTING_SOLD, LISTING_CERTIFICATION_REQUESTED -> buildStatusCommand(envelope, eventType);
    default -> null;
};
```

`EventType.fromRoutingKey()` safely handles unknown routing keys via `Optional`. Unknown event types are discarded with a `Log.warn` rather than throwing. The switch correctly routes to different builders based on payload shape. ✓

### PASS — Intentional Fan-Out on `listing.certification.requested`

Both the certification module and the notification module are subscribed to `listing.certification.requested`. This is correct architecture — each module maintains its own dedicated queue on the topic exchange. Each module receives an independent copy of the event. No cross-module coupling is introduced. ✓

### PASS — `price` Field in `ListingNotificationCommand` (Not Financial Data)

`ListingNotificationCommand` carries a nullable `price` field (BigDecimal). This is the listing price used for notification content (e.g., "Your listing was published at $21,000"). It is not payment, cardholder, or transaction data. No PCI scope is introduced. `price` is null for non-created events, which is handled correctly by `buildStatusCommand()`. ✓

### OBSERVATION — `extractLeafField()` Duplicated

**Files:** `GlobalExceptionMapper.java`, `ResteasyReactiveViolationExceptionMapper.java`
**Severity:** Minor — DRY violation

`extractLeafField()` is defined identically in both exception mappers:

```java
private String extractLeafField(String path) {
    int lastSeparator = path.lastIndexOf('.');
    return lastSeparator >= 0 ? path.substring(lastSeparator + 1) : path;
}
```

Note: OB-02 from V3 was closed because the field extraction logic was added. The duplication itself is a new minor observation.

**Recommendation for P3:** Extract to a static utility method in `ValidationErrorDetail` or a package-private helper in `ai.motoria.common.exception`:
```java
// ValidationErrorDetail.java
public static String leafField(String path) {
    int i = path.lastIndexOf('.');
    return i >= 0 ? path.substring(i + 1) : path;
}
```

### OBSERVATION — `EventType.fromRoutingKey()` Linear Scan

**File:** `EventType.java`
**Severity:** Minor — performance observation

```java
public static Optional<EventType> fromRoutingKey(String routingKey) {
    return Arrays.stream(values())
            .filter(e -> e.routingKey.equals(routingKey))
            .findFirst();
}
```

Linear scan over the enum values on every incoming message. With a small enum this is negligible. At high message throughput (thousands/second), a `Map<String, EventType>` lookup is O(1).

**Recommendation for P4 (if throughput warrants):**
```java
private static final Map<String, EventType> BY_ROUTING_KEY =
    Arrays.stream(values()).collect(Collectors.toUnmodifiableMap(e -> e.routingKey, e -> e));

public static Optional<EventType> fromRoutingKey(String routingKey) {
    return Optional.ofNullable(BY_ROUTING_KEY.get(routingKey));
}
```

---

## Section 4 — Test Suite Review

### `ListingTest.java` — PASS

**Assessment:** Correct. Covers all domain behavior.

| Test | Validates | Status |
|---|---|---|
| `createShouldInitializeListingInDraftState` | Factory sets id, status=DRAFT, timestamps equal | ✅ |
| `submitForReviewShouldMoveDraftToPendingReview` | DRAFT → PENDING_REVIEW | ✅ |
| `publishShouldMovePendingReviewToPublished` | PENDING_REVIEW → PUBLISHED | ✅ |
| `markSoldShouldMovePublishedToSold` | PUBLISHED → SOLD | ✅ |
| `invalidTransitionsShouldThrowIllegalStateException` | DRAFT→publish throws; DRAFT→markSold throws; PUBLISHED→submitForReview throws | ✅ |
| `updateDetailsShouldRefreshValuesAndTimestamp` | All fields updated; `updatedAt` strictly after original | ✅ |

No Quarkus container, no external dependencies. Tests are fast and deterministic. ✓

**Minor:** `Thread.sleep(5)` in `updateDetailsShouldRefreshValuesAndTimestamp` is necessary to create a measurable timestamp delta when `Instant.now()` resolution is millisecond-level. Acceptable. An injectable `Clock` would eliminate the sleep but is an over-engineering concern at this stage.

---

### `ListingServiceTest.java` — PASS

**Assessment:** Correct. Covers security-critical paths and correct delegation pattern.

| Test | Validates | Status |
|---|---|---|
| `createShouldSanitizeInputAndDelegateToPersistenceService` | XSS stripped from title and description before persistence; sellerId from JWT | ✅ |
| `updateShouldSanitizeInputAndDelegateToPersistenceService` | XSS stripped from updated title and description | ✅ |
| `getByIdShouldThrowWhenListingDoesNotExist` | `ListingNotFoundException` on missing ID | ✅ |
| `requestCertificationShouldRejectNonOwner` | `ForbiddenOperationException` + event NOT published | ✅ |
| `requestCertificationShouldPublishEventForOwner` | Event published; response returned | ✅ |

**Security-critical verification:** The sanitization test sends `<b>Fast Car</b>` and `<script>alert('x')</script>Clean description` and asserts the persistence service receives `"Fast Car"` and `"Clean description"`. The ArgumentCaptor pattern correctly intercepts the sanitized request before it reaches the mock. This is the right way to test XSS sanitization. ✓

**Architecture verification:** The test mocks `ListingPersistenceService` as a first-class dependency. This confirms the RG-01 fix architecture is reflected in the test design — `ListingService` is not expected to own `@Transactional` methods. ✓

**Ownership test:** `requestCertificationShouldRejectNonOwner` sets a different subject in JWT than the listing owner, then asserts `ForbiddenOperationException` is thrown and `verify(listingEventProducer, never()).certificationRequested(any())` — confirming no event is emitted before the ownership check. ✓

---

### `ListingResourceTest.java` — PASS

**Assessment:** Correct. Covers RBAC, authentication, validation response structure, and error codes via full Quarkus stack.

| Test | Validates | Status |
|---|---|---|
| `createShouldReturnCreatedForValidSellerRequest` | POST /listings as SELLER → 201, Location header, body.id matches | ✅ |
| `createShouldReturnValidationErrorsWithLeafFields` | Validation → 400, code=VALIDATION_ERROR, violations.field has `title` and `vehicleSpecId`, all fields have no `.` | ✅ |
| `createShouldRejectBuyerRole` | POST /listings as BUYER → 403 | ✅ |
| `getByIdShouldRequireAuthentication` | GET /listings/{id} unauthenticated → 401 | ✅ |
| `getByIdShouldReturnNotFoundWhenServiceThrows` | ListingNotFoundException → 404, code=NOT_FOUND | ✅ |
| `markSoldShouldReturnForbiddenWhenServiceRejectsNonOwner` | ForbiddenOperationException → 403, code=FORBIDDEN | ✅ |

**Validation structure test** is the most important here. The assertion:
```java
.body("violations.field", everyItem(not(containsString("."))))
```
confirms that `extractLeafField()` is working correctly end-to-end — no `create.arg0.title` style paths reach the client. ✓

**Redis mock setup** in `setUp()` correctly stubs `valueCommands.incr()` to return `1L`, which is below the rate limit threshold for all requests. Rate limiting does not interfere with any test. ✓

**Minor gap:** No test validates the rate limit enforcement itself at the HTTP layer (i.e., that a 429 is returned after N requests). Rate limiting is covered at the filter unit level (if a `RateLimitingFilterTest` exists). Acceptable for this sprint — HTTP-level rate limit tests are expensive to write with Quarkus test containers.

---

### `CertificationEventConsumerTest.java` — PASS with gaps

**Assessment:** Correct mechanics. Meaningful coverage gap.

| Test | Validates | Status |
|---|---|---|
| `shouldDispatchCertificationRequestedEvent` | Full happy-path — correlationId, sourceModule, listingId, listingStatus all mapped | ✅ |

Plain JUnit5/Mockito — no Quarkus container. Fast and correct. ✓

**Coverage gaps (non-blocking for sprint approval):**

1. No test for malformed JSON — `readEnvelope()` throws `IllegalArgumentException`. Adding this test would confirm the failure path and document the expected behavior before a DLQ is configured.
2. No test for a missing payload field (e.g., no `listingId` in payload) — would fail with Jackson deserialization error, same path as above.
3. No test for wrong `eventType` value in the raw JSON (e.g., `"listing.created"` arriving on this channel). Currently the consumer would still dispatch to `CertificationService` because there is no eventType guard.

**Recommendation for P2:** Add at minimum:
```java
@Test
void shouldThrowOnMalformedJson() {
    assertThrows(IllegalArgumentException.class,
        () -> consumer.onCertificationRequested("{invalid-json}"));
}
```

---

### `NotificationEventConsumerTest.java` — PASS with gaps

**Assessment:** Correct mechanics. Branch coverage gap.

| Test | Validates | Status |
|---|---|---|
| `shouldDispatchListingCreatedEvent` | Full happy-path for LISTING_CREATED — all fields including sellerId and price | ✅ |
| `shouldIgnoreUnsupportedEventType` | `listing.updated` → no interactions with service | ✅ |

`verifyNoInteractions(notificationService)` for the unsupported event test is the correct assertion — it confirms the resilience path works end-to-end. ✓

**Coverage gaps (non-blocking):**

The `buildStatusCommand()` branch covers `LISTING_PUBLISHED`, `LISTING_SOLD`, and `LISTING_CERTIFICATION_REQUESTED`. None of these are tested. The status command differs from the created command (no `sellerId`, no `price`). A regression in `buildStatusCommand()` would not be caught.

**Recommendation for P2:** Add tests for the status event branches:
```java
@Test
void shouldDispatchListingPublishedEvent() { ... }

@Test
void shouldDispatchListingCertificationRequestedEvent() { ... }
```

---

## Section 5 — Previous Fix Integrity Verification

All BLOCK and P1 fixes verified intact from V3. Sprint 2B introduced no regressions.

| Fix | Check | Status |
|---|---|---|
| SEC-01 Credentials | `${ENV_VAR}` placeholders only in properties | ✅ INTACT |
| SEC-02 sellerId from body | `CreateListingRequest` has no sellerId; JWT subject used | ✅ INTACT |
| SEC-03 Ownership guards | `assertOwnership()` in both service beans | ✅ INTACT |
| SEC-04 CORS wildcard | Origins/methods/headers explicitly scoped | ✅ INTACT |
| SEC-10 XSS sanitization | `PLAIN_TEXT_POLICY` applied; verified by `ListingServiceTest` | ✅ INTACT |
| ARCH-01 common→listing | `GlobalExceptionMapper` zero listing imports | ✅ INTACT |
| ARCH-04 Static routing key | Per-message `OutgoingRabbitMQMetadata` | ✅ INTACT |
| RG-01 @Transactional | `ListingPersistenceService` mocked in `ListingServiceTest` | ✅ INTACT |
| RG-02 Path normalization | Not changed in Sprint 2B | ✅ INTACT |
| RG-03 Anonymous rate limit | Not changed in Sprint 2B | ✅ INTACT |
| RG-04 Validation response | `code=VALIDATION_ERROR` verified in `ListingResourceTest` | ✅ INTACT |
| OB-02 Leaf field extraction | `everyItem(not(containsString(".")))` assertion passes | ✅ INTACT |

---

## Section 6 — Full Checklist Summary

### Review Agent Criteria

| Criterion | Result |
|---|---|
| Business logic in controllers | ✅ PASS — zero logic in consumers or resource; all delegates to services |
| Missing validation | ✅ PASS — event types validated; unknown events discarded with warn |
| Architecture violations | ✅ PASS — no cross-module imports; shared kernel only |

### OWASP Security Agent Checklist

| Control | Status | Evidence |
|---|---|---|
| Input validation | ✅ PASS | DTO validators; eventType guard in notification consumer; ownership enforced |
| Authentication | ✅ PASS | `getByIdShouldRequireAuthentication` → 401; `@RolesAllowed` enforced |
| Authorization | ✅ PASS | RBAC tested (SELLER vs BUYER); ownership tested (non-owner forbidden) |
| Sensitive data in events | ✅ PASS | `price` is listing price (not payment data); `sellerId` null in status events |
| Error handling | ✅ PASS | Structured error responses; no internal detail leakage |
| XSS protection | ✅ PASS | Sanitization verified by `ListingServiceTest` |
| Message resilience | ⚠️ OPEN | No DLQ configured — malformed messages could stall consumers |

### Code Review Skill Checklist

| Dimension | Status |
|---|---|
| Architecture | ✅ PASS — module isolation maintained; event normalization correct |
| Security | ✅ PASS — no new vulnerabilities; previous controls intact |
| Performance | ✅ PASS — pagination, AI call placement, `@Blocking` on consumers |
| Testing | ⚠️ PARTIAL — critical paths covered; branch coverage gaps in consumer tests |

---

## Section 7 — Updated Unresolved Items for Next Sprint

```
P2 — Sprint 3 (pre-deployment)

  1.  DLQ-01     Dead-letter queue for event consumers
                 Configure failure-strategy=dead-letter-queue on:
                   listing-certification-requested-in
                   listing-notification-events-in
                 Create motoria.events.dlq exchange and DLQ bindings.
                 Required before any consumer is deployed.

  2.  TEST-04    CertificationEventConsumerTest coverage gaps
                 Add: malformed JSON throws IllegalArgumentException.
                 Add: missing payload fields handled.
                 Add: wrong eventType on channel produces no dispatch (after
                      eventType guard is added).

  3.  TEST-05    NotificationEventConsumerTest branch coverage
                 Add: LISTING_PUBLISHED dispatches via buildStatusCommand
                      (sellerId=null, price=null).
                 Add: LISTING_SOLD dispatches correctly.
                 Add: LISTING_CERTIFICATION_REQUESTED dispatches correctly.
                 Add: malformed JSON throws IllegalArgumentException.

  4.  TYPE-01    CertificationRequestedPayload.status → ListingStatus enum
                 Change private record field from String to ListingStatus.
                 Jackson will validate at deserialization boundary.
                 File: CertificationEventConsumer.java (inner record)

  5.  CONS-01    CertificationEventConsumer eventType guard
                 Add check: if envelope.eventType() is not
                 listing.certification.requested, log warn and return.
                 Prevents silent misrouting if channel binding is ever misconfigured.

  6.  MOD-04     PromotionEventConsumer
                 Consume listing.published.
                 Delegate to Instagram/Facebook integrations.

P3 — Sprint 4 (pre-production hardening)

  7.  OB-04      Transactional outbox pattern
                 Required before any consumer is deployed to production.
                 See V3 report for implementation options.

  8.  SEC-11     Replace database.generation=update with Flyway
                 Add quarkus-flyway dependency.
                 Create V1__init_listing_schema.sql.

  9.  OB-03      DRY — remove assertOwnership duplication
                 Keep in ListingPersistenceService only.

  10. OB-01      X-Forwarded-For topology confirmation
                 Confirm WSO2 API Manager rewrites X-Forwarded-For.

  11. DRY-01     Extract extractLeafField() to shared utility
                 Remove duplication between GlobalExceptionMapper and
                 ResteasyReactiveViolationExceptionMapper.

  12. INT-01     HttpAiPriceIntegration
                 Implement when AI module is ready.
                 Replace StubAiPriceIntegration via CDI qualifier.

P4 — Future sprints

  13. PERF-03    Vehicle catalog Redis caching (@CacheResult)
  14. SEC-12     Per-role rate limit tiers (ADMIN higher limit)
  15. PERF-05    EventType.fromRoutingKey() — Map-based O(1) lookup
  16. MOD-05     Implement 11 scaffold modules in dependency order:
                 user → vehicle → partner → media → ai →
                 financing → inspection → subscription
  17. ARCH-09    ListingSearchRequest public fields → private with getters
  18. PERF-04    Index strategy for listing schema (status, category, price, modelYear)
```

---

*Review Report V4 — Motoria.ai Backend*
*Agents: review-agent · security-agent · architect-agent*
*Verdict: APPROVED — proceed to Sprint 3*
