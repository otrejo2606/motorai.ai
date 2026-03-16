# Motoria.ai — Backend Review Report V9 (OB-04: Transactional Outbox)

```
Agent   : review-agent.md · architect-agent.md · security-agent.md
Skills  : code-review.md · architecture.md · security-owasp.md
Prompt  : review-code.md
Source  : REVIEW_REPORT_V8.md (previous)
Date    : 2026-03-15
Scope   : OB-04 — Transactional outbox implementation
          New: OutboxEvent, OutboxEventStatus, OutboxEventRepository,
               OutboxEventPublisher, OutboxDispatcher
          Updated: DomainEventEnvelope, DomainEventPublisher,
                   RabbitMqEventPublisher, ListingEventProducer
          Tests: OutboxEventPublisherTest, OutboxDispatcherTest
```

---

## Overall Verdict

```
STATUS: APPROVED
```

The transactional outbox is correctly implemented. The core guarantee — outbox row committed atomically with the domain change — is in place. Direct broker publishing from the transactional path is eliminated. Multi-instance safety is correctly achieved via `FOR UPDATE SKIP LOCKED`. Scheduler concurrency is controlled. Module boundaries are clean. The ghost-event and silent-loss risks documented since V3 are resolved.

Six non-blocking observations are documented below. None block this sprint.

---

## Section 1 — Architecture

### PASS — Outbox Pattern is Correctly Structured

The full event publishing chain:

```
ListingPersistenceService (@Transactional)
  └─ listingRepository.persist(listing)          ← domain write, TX 1
  └─ listingEventProducer.listingCreated(listing) ← same TX 1
       └─ DomainEventPublisher.publish(...)
            └─ OutboxEventPublisher.publish(...)
                 └─ outboxEventRepository.persist(OutboxEvent.pending(...)) ← outbox write, TX 1

[separate process / scheduler tick]
OutboxDispatcher.dispatchPendingEvents()
  └─ outboxEventRepository.claimBatch(...)        ← TX 2: claim, markInProgress
  └─ rabbitMqEventPublisher.publish(outboxEvent)  ← outside any TX: broker send
  └─ outboxEventRepository.markSent(...)          ← TX 3: mark SENT
```

Domain write and outbox write share the same JTA transaction. A single atomic commit either writes both or neither. ✓

### PASS — Direct Broker Publishing Eliminated from Transactional Path

`ListingEventProducer` now injects `DomainEventPublisher` (interface), not `RabbitMqEventPublisher` directly. `OutboxEventPublisher` is the sole implementation of `DomainEventPublisher`. It writes to the database — never to the broker. `RabbitMqEventPublisher` is called only from `OutboxDispatcher`, which runs outside any domain transaction. ✓

Confirmation — `RabbitMqEventPublisher` is referenced only in:

| File | Role |
|---|---|
| `RabbitMqEventPublisher.java` | Declaration |
| `OutboxDispatcher.java` | Injected and called post-claim |
| `OutboxDispatcherTest.java` | Test mock |

Zero references from any domain service or event producer. ✓

### PASS — Module Boundaries Preserved

`ListingEventProducer` → `DomainEventPublisher` (common.event interface) — no import from `common.outbox`. ✓

`OutboxDispatcher` → `RabbitMqEventPublisher` (common.event) — acceptable, both in common. ✓

`OutboxEventPublisher` → `DomainEventPublisher` (common.event), `OutboxEventRepository` (common.outbox) — fully within common. ✓

No domain module (`listing`, `certification`, `notification`) imports from `common.outbox`. ✓

Certification and notification consumers are unchanged. Their code, imports, and test coverage are intact. ✓

### PASS — Event Payload Contracts Preserved

`DomainEventEnvelope` gains a `restore()` factory method used by `RabbitMqEventPublisher` to reconstruct the envelope from an `OutboxEvent` row:

```java
public static <T> DomainEventEnvelope<T> restore(
        UUID correlationId, String eventType, String sourceModule,
        Instant timestamp, int retryCount, T payload) { ... }
```

The `of()` factory is unchanged. Consumers still receive the same JSON shape: `{correlationId, eventType, sourceModule, timestamp, retryCount, payload}`. ✓

`ListingCreatedPayload` and `ListingStatusPayload` carry `ListingStatus` from `common.domain`. Unchanged. ✓

---

## Section 2 — Transactionality and Reliability

### PASS — Outbox Write is Atomic with Domain Write

`OutboxEventPublisher.publish()` calls `outboxEventRepository.persist()` (Panache `persist`). Panache's `persist()` participates in the caller's active transaction — it does not start its own. `ListingPersistenceService` is `@Transactional`, so the outbox persist joins that transaction. A single commit covers both the domain entity and the outbox row. ✓

Failure scenarios:

| Scenario | Outcome |
|---|---|
| DB fails before commit | TX rolls back → no listing, no outbox row |
| Listing persist fails | TX rolls back → no outbox row (no ghost event) |
| Outbox persist fails | TX rolls back → no listing row committed (domain consistent) |
| Successful commit | Both listing and outbox row persisted atomically |

OB-04 ghost-event and silent-loss risks are fully resolved. ✓

### PASS — Dispatcher Publish/Mark-Sent Flow is Correct

```java
void dispatchSingleEvent(OutboxEvent event) {
    try {
        rabbitMqEventPublisher.publish(event);
        outboxEventRepository.markSent(event.getId(), Instant.now());
    } catch (Exception exception) {
        outboxEventRepository.markFailed(event.getId(),
            Instant.now().plus(retryDelay), exception.getMessage());
    }
}
```

Publish succeeds → `markSent()` → status=SENT. ✓
Publish fails → `markFailed()` → status=FAILED, `retryCount++`, `availableAt = now + retryDelay`. ✓
Publish succeeds, `markSent()` fails (e.g., transient DB error) → row stays IN_PROGRESS → picked up again after `lockedUntil` expires → **at-least-once duplicate**. This is the expected and correct behavior for this pattern. ✓

### PASS — Stuck Message Recovery via Lock Expiry

The claim query includes:

```sql
or (status = 'IN_PROGRESS' and locked_until <= :now)
```

If a dispatcher pod crashes after claiming but before marking sent, the row stays IN_PROGRESS. After `claimDuration` (default 30s), any dispatcher can re-claim and re-publish. No manual intervention required. ✓

### OBSERVATION — No Maximum Retry Count (Poison Message Risk)

**Severity:** Medium — operational risk

`OutboxEvent.retryCount` increments on each `markFailed()`, but no guard in `OutboxDispatcher` or `OutboxEventRepository` stops retrying after N failures. An event with an unserializable payload (e.g., broker routing failure) cycles through FAILED → available → IN_PROGRESS → fail → FAILED indefinitely.

**Recommendation for P3:** Add a configurable maximum retry count (default e.g., 10). After exceeding it, move the row to a terminal `DEAD` status (or extend `OutboxEventStatus` with `DEAD_LETTER`). Alert on `DEAD` rows. This prevents a poison message from consuming dispatcher cycles indefinitely.

### OBSERVATION — Fixed Backoff, Not Exponential

**Severity:** Minor

`retryDelay` is a fixed `Duration` (default 30s). On repeated broker failures, each retry is delayed by the same 30s. A sustained outage fills the dispatcher cycle with retry attempts at a constant rate.

**Recommendation for P3:** Implement exponential backoff with jitter: `retryDelay * 2^min(retryCount, 6) + jitter`. Cap at a configured maximum (e.g., 10 minutes). This reduces load on a recovering broker and spreads retries.

---

## Section 3 — Concurrency and Operational Behavior

### PASS — `FOR UPDATE SKIP LOCKED` is Correct for Multi-Instance Safety

```sql
select id from outbox_event
where ((status = 'PENDING' or status = 'FAILED') and available_at <= :now)
   or (status = 'IN_PROGRESS' and locked_until <= :now)
order by occurred_at
for update skip locked
```

`SKIP LOCKED` prevents multiple instances from claiming the same batch. A row locked by instance A is invisible to instance B's query. Each instance claims a non-overlapping set. This is the correct PostgreSQL mechanism for concurrent queue consumers. ✓

`order by occurred_at` ensures FIFO dispatch ordering per event time. ✓

### PASS — Two-Query Claim Approach is Correct

The claim method first selects IDs via native query (with FOR UPDATE SKIP LOCKED), then loads entities via JPQL (`find("id in ?1", ids)`). Both queries run in the same `@Transactional` boundary:

1. Native query acquires row-level locks on the selected IDs
2. JPQL `find` reads the same rows — the same transaction owns the locks, so no blocking occurs
3. `markInProgress()` sets status=IN_PROGRESS and lockedUntil in-memory
4. On transaction commit, Hibernate flushes the dirty entities — a single batch UPDATE

Concurrent instances running `FOR UPDATE SKIP LOCKED` see IN_PROGRESS rows as locked and skip them. ✓

### PASS — Scheduler Concurrency Safety

```java
@Scheduled(every = "{motoria.outbox.dispatch.every:5s}",
    concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
void dispatchPendingEvents()
```

`ConcurrentExecution.SKIP` prevents a new scheduled execution from starting if the previous one is still running on the same instance. Combined with `FOR UPDATE SKIP LOCKED`, this ensures:
- No intra-instance concurrency on the dispatch loop
- No inter-instance conflicts on row claiming ✓

### PASS — Dispatcher Disabled in Test Profile

```properties
# test/application.properties
motoria.outbox.dispatch.enabled=false
```

The scheduler fires in test but `if (!enabled) return` short-circuits immediately. No outbox dispatch occurs during `@QuarkusTest` executions. No RabbitMQ dependency in tests. ✓

### OBSERVATION — `markSent()` / `markFailed()` Called Outside the Claim Transaction

`dispatchSingleEvent()` is not `@Transactional`. The claim (TX 2) commits before the broker publish. `markSent()` / `markFailed()` each start their own transaction (TX 3). If the application is killed between broker publish and `markSent()`, the row stays IN_PROGRESS and is reclaimed after lockedUntil — resulting in a duplicate dispatch. This is inherent to at-least-once delivery and is the correct trade-off. Consumers must be idempotent. The `correlationId` field on the envelope is the deduplication key for consumers.

**This is documented behavior, not a defect.** Consumers that require exactly-once semantics must implement idempotency on `correlationId`.

### OBSERVATION — `OutboxEventPublisher.publish()` Has No `@Transactional(MANDATORY)` Guard

**Severity:** Minor

`publish()` requires an active transaction (to participate in the domain TX). If called from a non-transactional context, Panache `persist()` will throw `TransactionRequiredException` with a non-obvious error message. Adding `@Transactional(TxType.MANDATORY)` would produce an immediate, clear error:

```java
// Defensive guard — explicitly requires caller to be @Transactional
@Transactional(Transactional.TxType.MANDATORY)
public <T> void publish(EventType eventType, String sourceModule, UUID aggregateId, T payload) { ... }
```

**Recommendation for P3:** Add `@Transactional(TxType.MANDATORY)` to `OutboxEventPublisher.publish()` to make the contract explicit and fail fast on misuse.

---

## Section 4 — Test Suite Review

### `OutboxEventPublisherTest.java` — PASS

| Test | Validates | Status |
|---|---|---|
| `shouldPersistPendingOutboxEvent` | `persist()` called with PENDING status, correct routing key, sourceModule, aggregateId, non-null correlationId, non-null timestamps, null sentAt, retryCount=0 | ✅ |

Correct use of `argThat` to verify the entity state at persist time. The `payloadJson.contains(aggregateId.toString())` check confirms serialization ran without asserting an exact JSON structure. ✓

**Coverage gap (non-blocking):** No test for the `JsonProcessingException` path in `publish()` (serialization failure → `IllegalStateException`). Difficult to trigger with a `Map` payload. Acceptable for this sprint.

---

### `OutboxDispatcherTest.java` — PASS

| Test | Validates | Status |
|---|---|---|
| `shouldMarkOutboxEventAsSentAfterSuccessfulPublish` | Successful publish → `markSent()` called with event ID | ✅ |
| `shouldMarkOutboxEventForRetryWhenPublishFails` | Broker exception → `markFailed()` called with error message | ✅ |
| `shouldClaimAndDispatchPendingEventsWhenEnabled` | `dispatchPendingEvents()` → `claimBatch()` invoked → publish called | ✅ |

The failure path test correctly verifies the error message is passed to `markFailed()`. ✓

The dispatch loop test confirms the wiring from scheduler tick through claim to broker publish. ✓

**Coverage gaps (non-blocking):**

1. No test for `enabled=false` early return — trivial, acceptable.
2. No test verifying `markFailed()` is NOT called when `markSent()` succeeds — implicit from test 1 but not explicitly asserted. The `verifyNoMoreInteractions` pattern would close this.
3. No test for `dispatchPendingEvents()` with an empty batch — `claimBatch` returns `[]` → no publish. Useful to confirm no NPE or empty-loop side effects.

All three are P3 improvements. The critical paths (happy, failure, loop) are covered. ✓

---

## Section 5 — Security Review

### PASS — No Sensitive Data Leakage

`OutboxEvent.lastError` captures `exception.getMessage()` and is stored in the DB (truncated to 1024 chars). It is not exposed via any API endpoint. No consumer test or resource test reads this field. ✓

`payloadJson` contains only listing domain data: UUIDs, status enum, price. No PII, no credentials, no cardholder data. ✓

### PASS — No Regression on Previously Approved Security Controls

| Control | Status |
|---|---|
| JWT ownership guards | ✅ INTACT — `ListingPersistenceService` unchanged |
| XSS sanitization | ✅ INTACT — `ListingService` unchanged |
| RBAC on all endpoints | ✅ INTACT — `ListingResource` unchanged |
| Rate limiting | ✅ INTACT — `RateLimitingFilter` unchanged |
| Audit logging | ✅ INTACT — `AuditInterceptor` unchanged |
| DLQ configuration | ✅ INTACT — `application.properties` unchanged |
| `ListingStatus` in `common.domain` | ✅ INTACT |
| All consumer tests | ✅ INTACT — 9 tests passing |

---

## Section 6 — Full Checklist Summary

### Review Agent Criteria

| Criterion | Result |
|---|---|
| Business logic in controllers | ✅ PASS |
| Missing validation | ✅ PASS |
| Architecture violations | ✅ PASS — clean module separation, interface-based decoupling |

### Architect Agent Criteria

| Criterion | Result |
|---|---|
| Outbox pattern correctness | ✅ PASS — atomic write, correct dispatch separation |
| Direct broker publish eliminated | ✅ PASS — only `OutboxDispatcher` calls `RabbitMqEventPublisher` |
| Multi-instance safety | ✅ PASS — `FOR UPDATE SKIP LOCKED` |
| Scheduler safety | ✅ PASS — `ConcurrentExecution.SKIP` |
| At-least-once delivery | ✅ PASS — duplicate-on-recovery, correlationId for idempotency |
| Stuck message recovery | ✅ PASS — `locked_until` expiry re-claims |

### OWASP Security Agent

| Control | Result |
|---|---|
| No sensitive data in events | ✅ PASS |
| No new API attack surface | ✅ PASS |
| All prior controls intact | ✅ PASS |

---

## Section 7 — Updated Unresolved Items

```
INFRASTRUCTURE (unchanged)

  DLQ-03    Pre-declare broker topology on RabbitMQ before deployment.
            Exchange: motoria.events.dlx (direct, durable)
            Queues:   certification.listing-certification-requested.dlq
                      notification.listing-events.dlq
            Bindings: DLX → each DLQ with configured routing keys.

P3 — Pre-production hardening

  SEC-11    Replace database.generation=update with Flyway.
            The outbox table is now a new production schema object.
            Create outbox_event migration script immediately.
            This is now URGENT — the outbox table must be versioned
            before any production deployment.

  OB-03     DRY — remove assertOwnership duplication.

  OB-01     X-Forwarded-For topology confirmation.

  DRY-01    Extract extractLeafField() to shared utility.

  INT-01    HttpAiPriceIntegration.

  OB-05     Outbox max retry / poison message detection.
            Add configurable max retry count.
            Move exhausted events to DEAD_LETTER status.
            Alert on DEAD_LETTER rows.

  OB-06     Exponential backoff for outbox retry delay.
            Replace fixed retryDelay with backoff:
            retryDelay * 2^min(retryCount, 6) + jitter, capped at max.

  OB-07     @Transactional(TxType.MANDATORY) on OutboxEventPublisher.publish().
            Fail fast with a clear error if called outside a transaction.

P2 — Pending sprint

  MOD-04    PromotionEventConsumer.

P4 — Future sprints

  PERF-05   EventType.fromRoutingKey() — Map-based O(1) lookup.
  PERF-03   Vehicle catalog Redis caching.
  SEC-12    Per-role rate limit tiers.
  MOD-05    11 scaffold modules.
  ARCH-09   ListingSearchRequest encapsulation.
  PERF-04   Index strategy for listing schema.
```

---

## Recommended Next Priority

**SEC-11 — Flyway migration is now urgent.**

The outbox implementation introduces the `outbox_event` table as a new production schema object. With `hibernate-orm.database.generation=update`, this table is currently created automatically on startup. Before any production deployment, the schema must be versioned under Flyway:

1. Add `quarkus-flyway` dependency
2. Create `V1__init_listing_schema.sql` (existing listing table)
3. Create `V2__add_outbox_event.sql` (new outbox_event table with all columns, NOT NULL constraints, unique constraint on `correlation_id`, index on `(status, available_at)` for the claim query)
4. Set `quarkus.hibernate-orm.database.generation=validate`

The index on `(status, available_at)` is particularly important for the `claimBatch` query, which filters on `status` and `available_at` on every scheduler tick (every 5s). Without an index, this is a full table scan at production scale.

After SEC-11, the remaining P3 hardening items (OB-05, OB-06, OB-07) should be addressed before the outbox is relied upon in a high-traffic production scenario.

---

*Review Report V9 — Motoria.ai Backend*
*Agents: review-agent · security-agent · architect-agent*
*Verdict: APPROVED — OB-04 resolved. Transactional outbox correctly implemented.*
*Next priority: SEC-11 (Flyway migration) — now urgent due to outbox_event table.*
