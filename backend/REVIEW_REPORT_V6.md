# Motoria.ai — Backend Review Report V6 (Sprint 2C — RV-01 Re-Review)

```
Agent   : review-agent.md · architect-agent.md · security-agent.md
Skills  : code-review.md · architecture.md · security-owasp.md
Prompt  : review-code.md
Source  : REVIEW_REPORT_V5.md (previous — REJECTED)
Date    : 2026-03-15
Scope   : RV-01 fix — ListingStatus promoted to ai.motoria.common.domain
          Full breadth check across all impacted files
```

---

## Overall Verdict

```
STATUS: APPROVED
```

RV-01 is fully resolved. `ListingStatus` is correctly placed in `ai.motoria.common.domain`. All 15 files that reference `ListingStatus` now import from the shared kernel. Zero files retain the old `ai.motoria.listing.domain.ListingStatus` path. Module boundaries are preserved. All previously approved Sprint 2C behavior is intact.

No new issues introduced.

---

## Section 1 — RV-01 Fix Validation

### RESOLVED — `certification` Module No Longer Imports `listing.domain`

**Previous state (V5 REJECTED):**
```java
// CertificationEventConsumer.java
import ai.motoria.listing.domain.ListingStatus;  // ← was BLOCK

// CertificationRequestedCommand.java
import ai.motoria.listing.domain.ListingStatus;  // ← was BLOCK
```

**Current state (V6):**
```java
// CertificationEventConsumer.java
import ai.motoria.common.domain.ListingStatus;  // ✓ shared kernel

// CertificationRequestedCommand.java
import ai.motoria.common.domain.ListingStatus;  // ✓ shared kernel

// CertificationEventConsumerTest.java
import ai.motoria.common.domain.ListingStatus;  // ✓ shared kernel
```

The cross-module coupling is eliminated. ✓

---

### VERIFIED — `ListingStatus` is in `ai.motoria.common.domain`

`ListingStatus.java` exists at `ai/motoria/common/domain/ListingStatus.java` with the following values:

```java
package ai.motoria.common.domain;

public enum ListingStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    SOLD,
    EXPIRED,
    REJECTED
}
```

Two new constants (`EXPIRED`, `REJECTED`) were added during the promotion. Neither is used by current `Listing` domain state machine methods. Both are forward-looking additions consistent with the full lifecycle of a listing on the platform. Acceptable. ✓

---

### VERIFIED — Zero Remaining References to Old Package

Grep for `ai.motoria.listing.domain.ListingStatus` across all source and test files:

```
Result: 0 files
```

The old package path is entirely absent from the codebase. The promotion is complete and consistent. ✓

---

### VERIFIED — All 15 Files Updated Correctly

Every file that uses `ListingStatus` now imports from `ai.motoria.common.domain`. Full impact matrix:

| File | Import | Status |
|---|---|---|
| `listing/domain/Listing.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/domain/ListingFilter.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/dto/ListingResponse.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/dto/ListingSummaryResponse.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/dto/ListingSearchRequest.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/event/ListingCreatedPayload.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/event/ListingStatusPayload.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/service/ListingPersistenceService.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/service/ListingService.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `certification/event/CertificationEventConsumer.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `certification/dto/CertificationRequestedCommand.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/domain/ListingTest.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/service/ListingServiceTest.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `listing/rest/ListingResourceTest.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |
| `certification/event/CertificationEventConsumerTest.java` | `ai.motoria.common.domain.ListingStatus` | ✅ |

---

## Section 2 — Module Boundary Analysis

The dependency graph after this fix:

```
listing     ──→  common.domain.ListingStatus  ✓
certification ──→  common.domain.ListingStatus  ✓
notification  ──→  (does not use ListingStatus — String in private records)  ✓

certification  →  listing  :  NONE  ✓  (RV-01 eliminated)
listing  →  certification  :  NONE  ✓
notification  →  listing   :  NONE  ✓
```

All three domain modules have zero direct inter-module dependencies. Cross-module communication routes exclusively through `ai.motoria.common`. ✓

`ListingStatus` is now a first-class shared event contract type. Its presence in `common.domain` correctly signals that this enum is part of the platform's cross-module vocabulary — any future module (financing, inspection, vehicle) that processes listing status values will import from the same location without needing to depend on the `listing` module. ✓

---

## Section 3 — Side-Effect Analysis

### Event Payload Types in `listing.event`

`ListingCreatedPayload` and `ListingStatusPayload` are now first-class files in `ai.motoria.listing.event`, using the typed `ListingStatus` enum. These are the listing module's outgoing event payload types — using the shared enum here is correct since they form part of the event contract published to RabbitMQ. ✓

The notification module's `NotificationEventConsumer` continues to use its own private `ListingCreatedPayload` and `ListingStatusPayload` records with `String status`. This is architecturally correct — the notification module may not import from `listing.event` (that would be a cross-module violation). Its private records are internal deserialization helpers, not shared types. The resulting `ListingNotificationCommand` uses `String listingStatus`, which is the notification module's internal representation. This asymmetry is intentional and acceptable.

### `EXPIRED` and `REJECTED` Constants

`Listing.java`'s state machine (`submitForReview()`, `publish()`, `markSold()`) does not yet implement transitions to `EXPIRED` or `REJECTED`. These constants exist in the enum but no domain method produces them today. No risk is introduced — unused enum values do not cause compile or runtime failures. When expiry or rejection flows are implemented in a future sprint, the states are already available in the shared contract. ✓

---

## Section 4 — Sprint 2C Behavior Integrity

All previously approved Sprint 2C behavior remains intact:

| Item | Status |
|---|---|
| `failure-strategy=reject` on both channels | ✅ INTACT |
| DLQ configuration (pending DLQ-02 verification) | ✅ INTACT |
| EventType guard in `CertificationEventConsumer` | ✅ INTACT |
| `CertificationRequestedCommand.listingStatus` is `ListingStatus` enum | ✅ INTACT |
| `CertificationEventConsumerTest` — 3 tests (happy, wrong type, malformed) | ✅ INTACT |
| `NotificationEventConsumerTest` — 6 tests (all branches + malformed) | ✅ INTACT |
| `ListingTest`, `ListingServiceTest`, `ListingResourceTest` — all import updated | ✅ INTACT |

---

## Section 5 — Full Checklist Summary

### Review Agent Criteria

| Criterion | Result |
|---|---|
| Business logic in controllers | ✅ PASS |
| Missing validation | ✅ PASS |
| Architecture violations | ✅ PASS — RV-01 resolved; zero cross-module imports |

### Architect Agent Criteria

| Criterion | Result |
|---|---|
| Module boundaries | ✅ PASS — listing, certification, notification all module-isolated |
| Shared kernel usage | ✅ PASS — `ListingStatus` correctly in `ai.motoria.common.domain` |
| Event contract | ✅ PASS — typed enum in event payloads |

### OWASP Security Agent Checklist

| Control | Status |
|---|---|
| All previous security controls | ✅ INTACT — no changes to security-relevant code |
| No new coupling via enum | ✅ PASS — enum promotion adds no runtime risk |

---

## Section 6 — Remaining Unresolved Items

All items carry forward from V5 with no changes in priority.

```
P2 — Pre-deployment (required before any consumer is deployed to production)

  DLQ-02    Verify SmallRye RabbitMQ DLQ property names
            Confirm that auto-bind-dlq, dead-letter-queue-name,
            dead-letter-exchange, dead-letter-routing-key are recognized
            by the SmallRye RabbitMQ connector version in use.
            If not, replace with queue-argument form:
              arguments[x-dead-letter-exchange]=motoria.events.dlx
              arguments[x-dead-letter-routing-key]=...
            Test against a real broker after startup to confirm the queue
            has x-dead-letter-exchange declared.

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
  MOD-05    Implement 11 scaffold modules
  ARCH-09   ListingSearchRequest encapsulation
  PERF-04   Index strategy for listing schema
```

---

## Recommended Next Priority

**Immediate:** DLQ-02 — Validate the SmallRye RabbitMQ DLQ property names against a real broker. This is the only remaining item that could silently undermine an already-approved resilience feature before the first deployment.

**After DLQ-02:** OB-04 (transactional outbox) is the next architectural prerequisite before any consumer goes to production. Without it, a DB commit + broker publish failure creates a ghost event scenario. This has been documented since V3 and becomes urgent once consumers move beyond stub implementations.

---

*Review Report V6 — Motoria.ai Backend*
*Agents: review-agent · security-agent · architect-agent*
*Verdict: APPROVED — Sprint 2C complete*
*RV-01 resolved. Next priority: DLQ-02 broker verification, then OB-04 transactional outbox.*
