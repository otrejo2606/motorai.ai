# Motoria.ai — Backend Review Report V8 (DLQ-02 Fix Re-Review)

```
Agent   : review-agent.md · architect-agent.md · security-agent.md
Skills  : code-review.md · architecture.md · security-owasp.md
Prompt  : review-code.md
Source  : REVIEW_REPORT_V7.md (previous — REJECTED)
Date    : 2026-03-15
Scope   : DLQ-02 fix — Spring AMQP properties replaced with SmallRye
          queue argument properties in application.properties
```

---

## Overall Verdict

```
STATUS: APPROVED
```

DLQ-02 is fully resolved. All four Spring AMQP property keys are absent. Both channels now carry the correct SmallRye RabbitMQ connector queue argument properties. The replacement comment is accurate. No regressions in architecture, consumer logic, or test coverage.

---

## Section 1 — DLQ-02 Fix Validation

### RESOLVED — Spring AMQP Properties Removed

Grep for all Spring AMQP DLQ property key patterns:

```
Pattern: mp.messaging.*.{auto-bind-dlq|dead-letter-queue-name|dead-letter-exchange|dead-letter-routing-key}=
Result:  0 matches
```

All four Spring AMQP keys are gone from `application.properties`. ✓

---

### RESOLVED — Correct SmallRye Queue Argument Properties Applied

Both incoming channels now carry the two SmallRye-native queue argument properties:

**Channel 1 — `listing-certification-requested-in`:**
```properties
mp.messaging.incoming.listing-certification-requested-in.failure-strategy=reject
mp.messaging.incoming.listing-certification-requested-in.queue.x-dead-letter-exchange=motoria.events.dlx
mp.messaging.incoming.listing-certification-requested-in.queue.x-dead-letter-routing-key=listing.certification.requested.dlq
```

**Channel 2 — `listing-notification-events-in`:**
```properties
mp.messaging.incoming.listing-notification-events-in.failure-strategy=reject
mp.messaging.incoming.listing-notification-events-in.queue.x-dead-letter-exchange=motoria.events.dlx
mp.messaging.incoming.listing-notification-events-in.queue.x-dead-letter-routing-key=listing.notification.dlq
```

The `queue.x-dead-letter-exchange` and `queue.x-dead-letter-routing-key` properties are the correct SmallRye Reactive Messaging RabbitMQ connector properties. When the application starts and the connector declares each queue on the broker, these arguments are included in the AMQP queue declaration (`x-dead-letter-exchange`, `x-dead-letter-routing-key`). Rejected messages are then routed to `motoria.events.dlx` with the configured routing key rather than being discarded. ✓

**Runtime behavior with corrected configuration:**

| Step | Behavior |
|---|---|
| Application starts | SmallRye declares queue with `x-dead-letter-exchange=motoria.events.dlx` |
| Message arrives on queue | Consumer processes normally |
| Consumer throws (malformed JSON, etc.) | SmallRye NACKs with `requeue=false` |
| Broker evaluates dead-letter routing | Finds `x-dead-letter-exchange` on queue → routes to DLX |
| DLX routes to DLQ | Message arrives at DLQ via configured routing key |

The infinite-retry risk is eliminated end-to-end. ✓

---

### RESOLVED — Inaccurate Comment Replaced

**V7 (rejected):**
```
# DLQ verified against SmallRye RabbitMQ incoming config: auto-bind-dlq, dead-letter-exchange,
# dead-letter-routing-key and dead-letter-queue-name are supported connector properties.
```
This was factually incorrect.

**V8 (current):**
```
# Dead-letter handling is configured through queue arguments only.
# The application sets x-dead-letter-exchange and x-dead-letter-routing-key on the main queues.
# The broker topology must be pre-provisioned; SmallRye does not auto-create the DLX or DLQ queues.
```

The replacement comment is accurate on all three points:
1. Dead-letter handling uses queue arguments — correct, `queue.x-*` are AMQP queue declaration arguments ✓
2. The application sets those arguments on queue declaration — correct, SmallRye will include them ✓
3. The broker must be pre-provisioned — correct, no SmallRye equivalent of `auto-bind-dlq` exists ✓

---

## Section 2 — Broker Pre-Provisioning Requirement (DLQ-03)

The comment correctly documents that the DLX exchange and DLQ queues must be pre-declared on the broker. This is a deployment/infrastructure concern, not a code concern. The following must exist before the application starts:

| Resource | Type | Properties |
|---|---|---|
| `motoria.events.dlx` | Exchange | durable, direct or topic |
| `certification.listing-certification-requested.dlq` | Queue | durable |
| `notification.listing-events.dlq` | Queue | durable |
| DLX → cert DLQ binding | Binding | routing-key: `listing.certification.requested.dlq` |
| DLX → notification DLQ binding | Binding | routing-key: `listing.notification.dlq` |

If the DLX exchange does not exist when the application starts, the broker will reject the main queue declaration that includes `x-dead-letter-exchange=motoria.events.dlx` and the consumer channel will fail to initialize.

This is documented in the comment and was tracked as DLQ-03 in V7. It is an infrastructure provisioning task, not a code change. It remains on the pre-deployment checklist.

---

## Section 3 — Architecture and Consumer Regression Check

No Java source files were modified. All previously approved components are unchanged:

| Component | Status |
|---|---|
| `CertificationEventConsumer` — module isolation, eventType guard | ✅ INTACT |
| `NotificationEventConsumer` — module isolation, multi-event routing | ✅ INTACT |
| `CertificationRequestedCommand` — `ListingStatus` from `common.domain` | ✅ INTACT |
| `ListingStatus` in `ai.motoria.common.domain` | ✅ INTACT |
| All consumer tests (9 total) | ✅ INTACT |
| All listing module tests (17 total) | ✅ INTACT |
| `failure-strategy=reject` on both channels | ✅ INTACT |
| All BLOCK and P1 fixes from V3 | ✅ INTACT |

---

## Section 4 — Full Checklist

### Review Agent Criteria

| Criterion | Result |
|---|---|
| Business logic in controllers | ✅ PASS |
| Missing validation | ✅ PASS |
| Architecture violations | ✅ PASS |

### Architect Agent Criteria

| Criterion | Result |
|---|---|
| Module boundaries | ✅ PASS |
| Event resilience | ✅ PASS — correct DLX routing on rejection |
| Configuration correctness | ✅ PASS — SmallRye queue argument properties applied |

### OWASP Security Agent

| Control | Result |
|---|---|
| No sensitive data exposed | ✅ PASS |
| Message failure handling | ✅ PASS — reject + DLX, no infinite retry |
| All prior security controls | ✅ INTACT |

---

## Section 5 — Updated Unresolved Items

```
INFRASTRUCTURE (pre-deployment, not a code change)

  DLQ-03    Pre-declare broker topology before deployment.
            Exchange: motoria.events.dlx (direct, durable)
            Queue:    certification.listing-certification-requested.dlq (durable)
            Queue:    notification.listing-events.dlq (durable)
            Bindings: DLX → each DLQ with the routing keys configured in properties.
            If the DLX does not exist on startup, queue declaration will fail.

P3 — Pre-production hardening

  OB-04     Transactional outbox pattern
            Required before any event consumer is deployed to production.

  SEC-11    Replace database.generation=update with Flyway

  OB-03     DRY — remove assertOwnership duplication

  OB-01     X-Forwarded-For topology — confirm WSO2 rewrites header

  DRY-01    Extract extractLeafField() to shared utility

  INT-01    HttpAiPriceIntegration

P2 — Pending sprint

  MOD-04    PromotionEventConsumer

P4 — Future sprints

  PERF-05   EventType.fromRoutingKey() — Map-based O(1) lookup
  PERF-03   Vehicle catalog Redis caching
  SEC-12    Per-role rate limit tiers
  MOD-05    11 scaffold modules
  ARCH-09   ListingSearchRequest encapsulation
  PERF-04   Index strategy for listing schema
```

---

## Recommended Next Priority

**OB-04 — Transactional outbox pattern.**

With the DLQ configuration now correct, consumers are resilient to malformed messages. The next gap that threatens production correctness is the atomicity between the database commit and the RabbitMQ publish. An event published inside `@Transactional` (as in `ListingPersistenceService`) can be dispatched before the transaction commits. A rollback after publish produces a ghost event; a broker failure after a successful commit produces silent event loss. Both outcomes become observable as soon as consumers are implemented beyond stub level.

OB-04 has been tracked since V3 and is the last P3 prerequisite with the broadest production impact.

---

*Review Report V8 — Motoria.ai Backend*
*Agents: review-agent · security-agent · architect-agent*
*Verdict: APPROVED — DLQ-02 resolved. Sprint 2C fully closed.*
*Next priority: OB-04 transactional outbox, then SEC-11 Flyway migration.*
