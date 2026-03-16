# Motoria.ai — Backend Review Report V5 (Sprint 2C)

```
Agent   : review-agent.md · architect-agent.md · security-agent.md
Skills  : code-review.md · architecture.md · security-owasp.md
Prompt  : review-code.md
Source  : REVIEW_REPORT_V4.md (previous)
Date    : 2026-03-15
Scope   : Sprint 2C — DLQ hardening, certification typing,
          eventType guard, expanded consumer test coverage
```

---

## Overall Verdict

```
STATUS: REJECTED
```

**One BLOCK-level architecture violation introduced.** The certification module now imports `ai.motoria.listing.domain.ListingStatus` in both its event consumer and its command DTO. This creates a direct compile-time dependency from the `certification` module onto the `listing` module's domain package — a cross-module coupling violation that breaks the module boundary contract established in V1 and confirmed in all previous review rounds.

All other Sprint 2C changes are correct and approvable. This one issue must be resolved before the sprint can be approved.

---

## Section 1 — BLOCK: Architecture Violation

### RV-01 — `certification` Module Imports `listing` Domain

**Severity:** BLOCK — compile-time cross-module dependency
**Files:**
- `ai/motoria/certification/event/CertificationEventConsumer.java:7`
- `ai/motoria/certification/dto/CertificationRequestedCommand.java:3`
- `ai/motoria/certification/event/CertificationEventConsumerTest.java:4`

```java
// CertificationEventConsumer.java
import ai.motoria.listing.domain.ListingStatus;  // ← BLOCK

// CertificationRequestedCommand.java
import ai.motoria.listing.domain.ListingStatus;  // ← BLOCK

// CertificationEventConsumerTest.java
import ai.motoria.listing.domain.ListingStatus;  // ← BLOCK
```

**The violation:** Sprint 2C applied TYPE-01 — replacing `String status` with the `ListingStatus` enum in the certification command. The implementation sourced `ListingStatus` directly from `ai.motoria.listing.domain`, giving the `certification` module a compile-time dependency on the `listing` module's internal domain layer.

**Why this is a BLOCK:**

The module boundary contract (defined in `BACKEND_ARCHITECTURE.md`, enforced since ARCH-01 in V1) prohibits cross-module domain imports. Modules communicate exclusively through the shared kernel (`ai.motoria.common`) and through message envelopes. The `listing` module's domain types — `ListingStatus`, `Listing`, `ListingCategory` — are internal to the `listing` module and must not leak across module boundaries.

This coupling creates a dependency chain that was not present before:

```
Before Sprint 2C:
  listing  ──events──→  certification    (runtime decoupled, no compile dependency)

After Sprint 2C:
  listing  ──events──→  certification
  listing  ←──import──  certification    (compile-time circular coupling)
```

If `ListingStatus` evolves (new enum constant, rename, package move, module split), the `certification` module breaks at compile time. The architecture principle of independent deployability is violated.

**Required fix — two acceptable options:**

**Option A (Recommended): Promote `ListingStatus` to the shared kernel**

Move `ListingStatus` to `ai.motoria.common.domain`. Both `listing` and `certification` modules may depend on `common`. This is the correct model: the shared kernel holds types that cross module boundaries as part of the event contract.

```
Before:
  ai.motoria.listing.domain.ListingStatus

After:
  ai.motoria.common.domain.ListingStatus
```

`listing.domain.Listing` would update its field type. `CertificationRequestedCommand` would import from `common`. `ListingStatus` in events becomes part of the formal event contract.

**Option B: Revert to `String` in the certification module, validate internally**

Keep `String listingStatus` in `CertificationRequestedCommand`. The certification service validates or maps the string to its own internal representation if needed. The event boundary remains untyped; type safety is enforced within the certification service, not at deserialization.

```java
// CertificationRequestedCommand.java — no listing import
public record CertificationRequestedCommand(
    UUID correlationId,
    String sourceModule,
    UUID listingId,
    String listingStatus) {}
```

Option A is preferred because it makes `ListingStatus` part of the formal shared event contract and preserves type safety end-to-end. Option B is acceptable if `ListingStatus` is not yet stable enough to be promoted to common.

---

## Section 2 — Event Resilience Review

### PASS — `failure-strategy=reject` Eliminates Infinite Retry

```properties
mp.messaging.incoming.listing-certification-requested-in.failure-strategy=reject
mp.messaging.incoming.listing-notification-events-in.failure-strategy=reject
```

With `failure-strategy=reject`, when a consumer method throws (e.g., `IllegalArgumentException` from malformed JSON), SmallRye Reactive Messaging NACKs the message to RabbitMQ with `requeue=false`. The broker does not redeliver the message to the same queue. The infinite retry loop risk (DLQ-01 from V4) is eliminated at the connector level. ✓

### PASS — Dedicated Dead-Letter Exchange Design

A dedicated `motoria.events.dlx` exchange is declared for both channels, separate from the main `motoria.events` exchange. This is the correct pattern — dead-lettered messages do not re-enter the live exchange topology. Each channel has its own named DLQ:

| Channel | DLQ Name |
|---|---|
| `listing-certification-requested-in` | `certification.listing-certification-requested.dlq` |
| `listing-notification-events-in` | `notification.listing-events.dlq` |

Named DLQs per logical flow allow independent monitoring, replay, and alerting. ✓

### OBSERVATION — DLQ Property Names May Be Unrecognized by SmallRye RabbitMQ Connector

**Severity:** Medium — operational risk, not a compile or runtime failure in tests
**File:** `application.properties:40–43, 53–56`

```properties
mp.messaging.incoming.listing-certification-requested-in.auto-bind-dlq=true
mp.messaging.incoming.listing-certification-requested-in.dead-letter-queue-name=certification.listing-certification-requested.dlq
mp.messaging.incoming.listing-certification-requested-in.dead-letter-exchange=motoria.events.dlx
mp.messaging.incoming.listing-certification-requested-in.dead-letter-routing-key=listing.certification.requested.dlq
```

These property names (`auto-bind-dlq`, `dead-letter-queue-name`, `dead-letter-exchange`, `dead-letter-routing-key`) are consistent with Spring AMQP's listener container configuration, not with the SmallRye Reactive Messaging RabbitMQ connector's documented property set.

The SmallRye RabbitMQ connector configures dead-letter exchange on the queue via AMQP queue arguments:

```properties
# SmallRye RabbitMQ — correct property format for DLX binding
mp.messaging.incoming.listing-certification-requested-in.arguments[x-dead-letter-exchange]=motoria.events.dlx
mp.messaging.incoming.listing-certification-requested-in.arguments[x-dead-letter-routing-key]=listing.certification.requested.dlq
```

If `auto-bind-dlq=true` and the related properties are not recognized by SmallRye, they are silently ignored. The consequence is:

- `failure-strategy=reject` will still NACK the message with `requeue=false` ✓
- But the queue will have no `x-dead-letter-exchange` argument declared on the broker
- RabbitMQ will discard the rejected message rather than routing it to the DLX
- **Dead-lettered messages are silently lost, not captured in the DLQ**

`mvn test passed` does not surface this issue because unit tests do not connect to a real RabbitMQ broker.

**Recommendation for P2 (before any consumer is deployed to production):**

Verify that these properties are recognized by the SmallRye RabbitMQ connector version in use. If not, replace with the queue-arguments form above. Validate against a real broker by confirming the queue has `x-dead-letter-exchange` declared in its properties after startup.

---

## Section 3 — CertificationEventConsumer Logic Review

### PASS — EventType Guard is Correct

```java
if (!EventType.LISTING_CERTIFICATION_REQUESTED.routingKey().equals(envelope.eventType())) {
    Log.warnf("Ignoring unexpected certification event type %s", envelope.eventType());
    return;
}
```

The guard correctly compares the envelope's string `eventType` field against the expected routing key constant from `EventType`. Unknown or misrouted events are discarded with a `Log.warn` before any payload processing occurs. No service method is called for unexpected event types. ✓

The ordering is also correct: the guard executes after deserialization but before payload extraction. A malformed envelope (that can still be partially deserialized with a readable `eventType`) is caught by the guard. A completely unparseable message is caught by `readEnvelope()` before reaching the guard. ✓

### PASS — Payload Extraction is Clean

```java
CertificationRequestedPayload payload = envelope.payload();
certificationService.handleCertificationRequested(new CertificationRequestedCommand(
        envelope.correlationId(),
        envelope.sourceModule(),
        payload.listingId(),
        payload.status()));
```

The consumer accesses `envelope.payload()` only after the eventType guard. No null check is needed on `payload` because `DomainEventEnvelope<CertificationRequestedPayload>` is deserialized as a typed generic — if `payload` is missing from the JSON, Jackson will produce `null` or throw during `readEnvelope()`, depending on nullability configuration. This is acceptable; the consumer does not need an additional null guard.

### PASS — Module Isolation (notification module unaffected)

`NotificationEventConsumer` is unchanged from V4. It imports only from `ai.motoria.common.event` (shared kernel) and `ai.motoria.notification.*` (module-local). No cross-module imports. ✓

---

## Section 4 — Test Suite Review

### `CertificationEventConsumerTest.java` — PASS (coverage complete for scope)

Three tests now cover the full consumer path:

| Test | Path Covered | Status |
|---|---|---|
| `shouldDispatchCertificationRequestedEvent` | Happy path — correct field mapping including `ListingStatus.PENDING_REVIEW` | ✅ |
| `shouldIgnoreWrongEventType` | Guard path — `listing.created` on certification channel → no service interaction | ✅ |
| `shouldRejectMalformedJson` | Error path — `{invalid-json` → `IllegalArgumentException`, no service interaction | ✅ |

All three critical consumer paths are tested. Coverage gaps from V4 (TEST-04) are resolved. ✓

**Note:** The test imports `ai.motoria.listing.domain.ListingStatus` directly (line 4) — this is a consequence of RV-01. When the architecture violation is fixed, this import must also be updated to reference the relocated type.

### `NotificationEventConsumerTest.java` — PASS (coverage complete for scope)

Six tests now cover all consumer branches:

| Test | Path Covered | Status |
|---|---|---|
| `shouldDispatchListingCreatedEvent` | LISTING_CREATED — full field mapping including sellerId and price | ✅ |
| `shouldDispatchListingPublishedEvent` | LISTING_PUBLISHED — status event, sellerId=null, price=null | ✅ |
| `shouldDispatchListingSoldEvent` | LISTING_SOLD — status event, sellerId=null, price=null | ✅ |
| `shouldDispatchCertificationRequestedEvent` | LISTING_CERTIFICATION_REQUESTED — status event | ✅ |
| `shouldIgnoreUnsupportedEventType` | `listing.updated` → no service interaction | ✅ |
| `shouldRejectMalformedJson` | `{invalid-json` → `IllegalArgumentException` | ✅ |

All V4 TEST-05 gaps are resolved. The `assertStatusEventDispatched()` helper is clean — extracts the common JSON template and assertion logic for the three status event branches without test duplication. ✓

**Null assertions in status event tests are explicit and correct:**

```java
&& command.sellerId() == null
&& command.price() == null
```

These confirm that `buildStatusCommand()` correctly omits seller and price data (not present in status event payloads). ✓

---

## Section 5 — Security Review

### PASS — No Sensitive Payload Leakage

`CertificationRequestedCommand` contains: `correlationId`, `sourceModule`, `listingId`, `listingStatus`. No personally identifiable information, no financial data, no credentials. ✓

`ListingNotificationCommand` carries nullable `price` (listing price, not payment data) and `sellerId` (internal UUID, not a personal identifier). No PCI or PII scope introduced. ✓

### PASS — No Unsafe Deserialization

Both consumers use `TypeReference<DomainEventEnvelope<T>>` for the envelope. Jackson processes known record types (`CertificationRequestedPayload`, `ListingCreatedPayload`, `ListingStatusPayload`) with explicit field sets. Polymorphic deserialization is not used — there is no `@JsonTypeInfo` or `enableDefaultTyping()`. Jackson's default configuration does not allow class-name based deserialization, which eliminates the deserialization gadget chain risk. ✓

### PASS — No Regression on Previously Approved Fixes

All BLOCK and P1 fixes from V3 remain intact. Sprint 2C changes are scoped entirely to the certification and notification event consumers, `application.properties`, and their tests. No changes were made to: `ListingService`, `ListingPersistenceService`, `RateLimitingFilter`, `GlobalExceptionMapper`, `SecurityHeadersFilter`, or any other previously reviewed component. ✓

### PASS — Disconnected Subscriber Warnings are Non-Blocking

The `mp.messaging.incoming.*.enabled=false` entries in `test/application.properties` disable both RabbitMQ channels in the test profile. SmallRye may emit `WARN` logs about disconnected subscribers at test startup — these are expected and benign. The channels are correctly disabled for unit/integration tests that do not connect to a real broker. ✓

---

## Section 6 — Previous Fix Integrity

| Fix | Check | Status |
|---|---|---|
| ARCH-01 common→listing | `GlobalExceptionMapper` — zero listing imports | ✅ INTACT |
| ARCH-04 Static routing key | `OutgoingRabbitMQMetadata` per message | ✅ INTACT |
| RG-01 @Transactional | `ListingPersistenceService` unchanged | ✅ INTACT |
| SEC-01 Credentials | `${ENV_VAR}` only — no new hardcoded values in properties | ✅ INTACT |
| SEC-10 XSS sanitization | Unchanged | ✅ INTACT |
| SEC-10 RBAC | Unchanged | ✅ INTACT |
| DLQ-01 Infinite retry | `failure-strategy=reject` eliminates redelivery loop | ✅ RESOLVED |
| CONS-01 EventType guard | Guard added to `CertificationEventConsumer` | ✅ RESOLVED |
| TEST-04 Cert consumer coverage | Three tests: happy path, wrong type, malformed JSON | ✅ RESOLVED |
| TEST-05 Notification branches | Six tests including all status event branches | ✅ RESOLVED |

---

## Section 7 — Full Checklist Summary

### Review Agent Criteria

| Criterion | Result |
|---|---|
| Business logic in controllers | ✅ PASS |
| Missing validation | ✅ PASS — eventType guard, malformed JSON handled |
| Architecture violations | ❌ FAIL — `certification` imports `listing.domain.ListingStatus` (RV-01) |

### OWASP Security Agent Checklist

| Control | Status |
|---|---|
| Input validation | ✅ PASS |
| Authentication | ✅ PASS |
| Authorization | ✅ PASS |
| Sensitive data in events | ✅ PASS |
| Unsafe deserialization | ✅ PASS |
| Error handling | ✅ PASS — reject + DLQ eliminates infinite retry |
| DLQ property correctness | ⚠️ VERIFY — property names may be unrecognized by SmallRye connector |

### Code Review Skill Checklist

| Dimension | Status |
|---|---|
| Architecture | ❌ FAIL — cross-module import (RV-01) |
| Security | ✅ PASS |
| Event resilience | ✅ PASS — `failure-strategy=reject` correct |
| Testing | ✅ PASS — all Sprint 2C branches covered |

---

## Section 8 — Required Fix for Approval

### RV-01 — Resolution Required

Apply one of the two options from Section 1 before resubmission.

**Option A (Recommended):** Move `ListingStatus` to `ai.motoria.common.domain`.

Files to update:
1. Create `ai/motoria/common/domain/ListingStatus.java` (copy of current enum)
2. Update `ai/motoria/listing/domain/Listing.java` — update import
3. Update `ai/motoria/listing/domain/ListingFilter.java` — update import (if used)
4. Update `ai/motoria/listing/dto/ListingResponse.java` — update import (if used)
5. Update `ai/motoria/listing/mapper/ListingMapper.java` — update import (if used)
6. Update `ai/motoria/certification/event/CertificationEventConsumer.java` — change to `common` import
7. Update `ai/motoria/certification/dto/CertificationRequestedCommand.java` — change to `common` import
8. Update `ai/motoria/certification/event/CertificationEventConsumerTest.java` — change to `common` import
9. Delete `ai/motoria/listing/domain/ListingStatus.java` (or keep as a `@Deprecated` re-export if backward compatibility is needed)

**Option B:** Revert certification module to `String listingStatus`.

Files to update:
1. `ai/motoria/certification/dto/CertificationRequestedCommand.java` — revert field type to `String`
2. `ai/motoria/certification/event/CertificationEventConsumer.java` — remove `ListingStatus` import, pass `payload.status().name()` or keep payload field as String
3. `ai/motoria/certification/event/CertificationEventConsumerTest.java` — update assertion to `command.listingStatus().equals("PENDING_REVIEW")`

---

## Section 9 — Updated Unresolved Items

```
REQUIRED BEFORE REAPPROVAL

  RV-01     Cross-module import — certification imports listing.domain.ListingStatus
            Fix: move ListingStatus to ai.motoria.common.domain (Option A)
                 or revert to String in certification module (Option B)
            Files: CertificationEventConsumer.java, CertificationRequestedCommand.java,
                   CertificationEventConsumerTest.java

P2 — Post-approval, pre-deployment

  DLQ-02    Verify SmallRye RabbitMQ DLQ property name format
            Test against real RabbitMQ broker to confirm x-dead-letter-exchange
            is declared on the queue after startup.
            If auto-bind-dlq is not recognized, replace with:
              arguments[x-dead-letter-exchange]=motoria.events.dlx
              arguments[x-dead-letter-routing-key]=...
            Required before any consumer is deployed to production.

  MOD-04    PromotionEventConsumer
            Consume listing.published.
            Delegate to Instagram/Facebook integrations.

P3 — Pre-production hardening

  OB-04     Transactional outbox pattern
            Required before any event consumer is deployed to production.

  SEC-11    Replace database.generation=update with Flyway

  OB-03     DRY — remove assertOwnership duplication

  OB-01     X-Forwarded-For topology — confirm WSO2 rewrites header

  DRY-01    Extract extractLeafField() to shared utility

  INT-01    HttpAiPriceIntegration — implement when AI module ready

P4 — Future sprints

  PERF-05   EventType.fromRoutingKey() — Map-based O(1) lookup
  PERF-03   Vehicle catalog Redis caching (@CacheResult)
  SEC-12    Per-role rate limit tiers
  MOD-05    Implement 11 scaffold modules (user → vehicle → ... → subscription)
  ARCH-09   ListingSearchRequest encapsulation
  PERF-04   Index strategy for listing schema
```

---

## Recommended Next Priority After RV-01 Fix

Once the `ListingStatus` placement is resolved (Option A or B) and tests pass, the sprint can be re-submitted. If Option A is applied, that also closes the foundation for other modules (`vehicle`, `user`) that will likely need `ListingStatus` or similar shared domain types.

After Sprint 2C approval, the recommended priority sequence:

1. **DLQ-02** — Verify DLQ connector properties against a real broker before any consumer deployment
2. **OB-04** — Transactional outbox pattern — required before event consumers go to production
3. **SEC-11** — Flyway migration — required before first database schema change in production
4. **MOD-04** — PromotionEventConsumer

---

*Review Report V5 — Motoria.ai Backend*
*Agents: review-agent · security-agent · architect-agent*
*Verdict: REJECTED — one BLOCK (RV-01: cross-module import certification→listing.domain)*
*Re-submit after resolving RV-01. All other Sprint 2C changes are approved.*
