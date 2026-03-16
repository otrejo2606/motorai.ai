# Motoria.ai — Backend Review Report V7 (DLQ-02 Hardening)

```
Agent   : review-agent.md · architect-agent.md · security-agent.md
Skills  : code-review.md · architecture.md · security-owasp.md
Prompt  : review-code.md
Source  : REVIEW_REPORT_V6.md (previous)
Date    : 2026-03-15
Scope   : DLQ-02 — RabbitMQ dead-letter configuration hardening
          Connector: io.quarkus:quarkus-messaging-rabbitmq (Quarkus BOM 3.15.1)
          Underlying connector: SmallRye Reactive Messaging RabbitMQ
```

---

## Overall Verdict

```
STATUS: REJECTED
```

**One BLOCK: the DLQ property names are not recognized by the SmallRye RabbitMQ connector.**

The only change introduced in this sprint is a 5-line comment asserting that the existing properties have been verified. The actual property names are identical to V5. The comment's claim is inaccurate: `auto-bind-dlq`, `dead-letter-queue-name`, `dead-letter-exchange`, and `dead-letter-routing-key` are Spring AMQP property names. The SmallRye Reactive Messaging RabbitMQ connector (`io.quarkus:quarkus-messaging-rabbitmq`) uses a different property namespace for dead-letter queue arguments. As a result, these four properties are silently ignored at runtime and the dead-letter routing is not configured on the broker queue.

`failure-strategy=reject` still fires correctly — messages are NACKed with `requeue=false` — but rejected messages are discarded by the broker, not captured in a DLQ. The resilience goal of DLQ-01 has not been achieved.

---

## Section 1 — Change Analysis

### What was changed

**Only lines 31–35 were added:**

```properties
# DLQ verified against SmallRye RabbitMQ incoming config: auto-bind-dlq, dead-letter-exchange,
# dead-letter-routing-key and dead-letter-queue-name are supported connector properties.
# Broker verification example: rabbitmqctl list_queues name arguments
# or inspect the queue in the RabbitMQ Management UI and confirm x-dead-letter-exchange
# and x-dead-letter-routing-key are present on the declared queue.
```

The four DLQ property values (lines 45–48 and 58–61) are identical to V5. No property names were corrected.

---

## Section 2 — BLOCK: Incorrect DLQ Property Names for SmallRye Connector

### Connector identification

The project uses `io.quarkus:quarkus-messaging-rabbitmq` from Quarkus BOM 3.15.1 (`pom.xml:58`). This artifact provides the SmallRye Reactive Messaging RabbitMQ connector, identified by `connector=smallrye-rabbitmq` in `application.properties:36,49`.

### SmallRye RabbitMQ connector property namespace

The SmallRye Reactive Messaging RabbitMQ connector configures queue arguments — including dead-letter routing — by prefixing the AMQP queue argument name with `queue.`:

```properties
# SmallRye RabbitMQ — correct dead-letter queue argument properties
mp.messaging.incoming.[channel].queue.x-dead-letter-exchange=<dlx-exchange-name>
mp.messaging.incoming.[channel].queue.x-dead-letter-routing-key=<dlq-routing-key>
```

These map directly to AMQP queue declaration arguments (`x-dead-letter-exchange`, `x-dead-letter-routing-key`). When the connector declares the queue on startup, it includes these arguments. The broker then routes NACKed messages to the configured exchange with the configured routing key.

### Spring AMQP property namespace (incorrect for this project)

The properties currently in `application.properties` use a different namespace:

```properties
# In application.properties — these are Spring AMQP property names, not SmallRye
auto-bind-dlq=true
dead-letter-queue-name=certification.listing-certification-requested.dlq
dead-letter-exchange=motoria.events.dlx
dead-letter-routing-key=listing.certification.requested.dlq
```

`auto-bind-dlq`, `dead-letter-queue-name`, `dead-letter-exchange`, and `dead-letter-routing-key` are properties of Spring AMQP's `SimpleMessageListenerContainer` and `@RabbitListener` infrastructure, not the SmallRye connector. The SmallRye connector does not recognize these names. Quarkus logs an `UNKNOWN` or silently drops any MicroProfile Config property it cannot map to a known connector attribute.

### Consequence at runtime

The runtime behavior with the current configuration:

| Property | Recognized by SmallRye | Effect |
|---|---|---|
| `failure-strategy=reject` | ✅ Yes | Messages are NACKed with `requeue=false` |
| `auto-bind-dlq=true` | ❌ No — silently ignored | DLQ queue is not auto-created |
| `dead-letter-queue-name=...` | ❌ No — silently ignored | DLQ name is not registered |
| `dead-letter-exchange=...` | ❌ No — silently ignored | `x-dead-letter-exchange` queue argument not set |
| `dead-letter-routing-key=...` | ❌ No — silently ignored | `x-dead-letter-routing-key` queue argument not set |

When a message is rejected:
1. SmallRye NACKs it with `requeue=false` ✓
2. The broker checks the queue's `x-dead-letter-exchange` argument
3. Because `x-dead-letter-exchange` was never set (the property was ignored), the broker discards the message
4. **The message is lost. The DLQ receives nothing.**

This is the same failure mode identified in V5. The comment added in this sprint does not change the runtime behavior.

### Comment accuracy

The comment states:

```
# DLQ verified against SmallRye RabbitMQ incoming config: auto-bind-dlq, dead-letter-exchange,
# dead-letter-routing-key and dead-letter-queue-name are supported connector properties.
```

This claim is inaccurate. These properties are not documented in the SmallRye Reactive Messaging RabbitMQ connector specification. Documenting incorrect behavior in a comment creates a false confidence that the DLQ is operational when it is not.

---

## Section 3 — Required Fix

### Replace Spring AMQP properties with SmallRye queue arguments

For each incoming channel, remove the four unrecognized properties and add the two SmallRye queue argument properties:

**Channel 1 — certification:**

```properties
# Remove:
mp.messaging.incoming.listing-certification-requested-in.auto-bind-dlq=true
mp.messaging.incoming.listing-certification-requested-in.dead-letter-queue-name=certification.listing-certification-requested.dlq
mp.messaging.incoming.listing-certification-requested-in.dead-letter-exchange=motoria.events.dlx
mp.messaging.incoming.listing-certification-requested-in.dead-letter-routing-key=listing.certification.requested.dlq

# Add:
mp.messaging.incoming.listing-certification-requested-in.queue.x-dead-letter-exchange=motoria.events.dlx
mp.messaging.incoming.listing-certification-requested-in.queue.x-dead-letter-routing-key=listing.certification.requested.dlq
```

**Channel 2 — notification:**

```properties
# Remove:
mp.messaging.incoming.listing-notification-events-in.auto-bind-dlq=true
mp.messaging.incoming.listing-notification-events-in.dead-letter-queue-name=notification.listing-events.dlq
mp.messaging.incoming.listing-notification-events-in.dead-letter-exchange=motoria.events.dlx
mp.messaging.incoming.listing-notification-events-in.dead-letter-routing-key=listing.notification.dlq

# Add:
mp.messaging.incoming.listing-notification-events-in.queue.x-dead-letter-exchange=motoria.events.dlx
mp.messaging.incoming.listing-notification-events-in.queue.x-dead-letter-routing-key=listing.notification.dlq
```

### DLQ queue and DLX exchange must be pre-declared

`auto-bind-dlq=true` has no SmallRye equivalent. The SmallRye connector will set the `x-dead-letter-exchange` and `x-dead-letter-routing-key` arguments on the main queue, but it will not create the DLX exchange or the DLQ queues. These must exist on the broker before the application starts, otherwise the queue declaration will fail (or dead-lettered messages will be dropped because the DLX has no bound DLQ).

Required infrastructure declarations (via RabbitMQ Management UI, `rabbitmqctl`, Terraform, or a startup initialization bean):

```
Exchange:  motoria.events.dlx  (type: direct or topic, durable: true)

Queue:     certification.listing-certification-requested.dlq  (durable: true)
Binding:   motoria.events.dlx  →  certification.listing-certification-requested.dlq
           routing-key: listing.certification.requested.dlq

Queue:     notification.listing-events.dlq  (durable: true)
Binding:   motoria.events.dlx  →  notification.listing-events.dlq
           routing-key: listing.notification.dlq
```

### Verification after fix

After applying the corrected properties and starting the application against a real broker:

```bash
rabbitmqctl list_queues name arguments
```

Expected output for the certification queue:
```
certification.listing-certification-requested
  x-dead-letter-exchange: motoria.events.dlx
  x-dead-letter-routing-key: listing.certification.requested.dlq
```

If `x-dead-letter-exchange` is absent from the queue arguments, the connector properties are still not being applied correctly.

Alternatively, inspect via the RabbitMQ Management UI:
- Navigate to Queues → `certification.listing-certification-requested`
- Confirm `x-dead-letter-exchange` and `x-dead-letter-routing-key` are present under "Features"

---

## Section 4 — Approved Items Unchanged

All previously approved architecture, consumer logic, and tests are intact. No regressions were introduced. The only change in this sprint was the comment addition.

| Previously approved item | Status |
|---|---|
| `failure-strategy=reject` — eliminates infinite retry | ✅ INTACT |
| EventType guard in `CertificationEventConsumer` | ✅ INTACT |
| `ListingStatus` in `ai.motoria.common.domain` | ✅ INTACT |
| All consumer tests (9 total across 2 consumers) | ✅ INTACT |
| All listing module tests (17 total) | ✅ INTACT |
| Module isolation — zero cross-module imports | ✅ INTACT |

---

## Section 5 — Updated Unresolved Items

```
REQUIRED BEFORE REAPPROVAL

  DLQ-02    Replace Spring AMQP property names with SmallRye queue argument properties.
            Replace in application.properties:
              auto-bind-dlq, dead-letter-queue-name, dead-letter-exchange,
              dead-letter-routing-key
            With:
              queue.x-dead-letter-exchange, queue.x-dead-letter-routing-key
            Also remove the inaccurate "verified" comment or replace with
            accurate documentation of the correct SmallRye property form.

  DLQ-03    Pre-declare DLX exchange and DLQ queues on the broker.
            motoria.events.dlx (direct, durable)
            certification.listing-certification-requested.dlq (durable)
            notification.listing-events.dlq (durable)
            Bindings from DLX to each DLQ with the configured routing keys.
            No SmallRye equivalent of auto-bind-dlq — broker state must be
            pre-provisioned before application startup.

P3 — Pre-production hardening (unchanged)

  OB-04     Transactional outbox pattern
  SEC-11    Replace database.generation=update with Flyway
  OB-03     DRY — remove assertOwnership duplication
  OB-01     X-Forwarded-For topology confirmation
  DRY-01    Extract extractLeafField() to shared utility
  INT-01    HttpAiPriceIntegration

P2 — Pending sprint

  MOD-04    PromotionEventConsumer
```

---

*Review Report V7 — Motoria.ai Backend*
*Agents: review-agent · security-agent · architect-agent*
*Verdict: REJECTED — DLQ-02 property names are Spring AMQP, not SmallRye. Messages will be discarded on rejection.*
*Fix: replace with queue.x-dead-letter-exchange and queue.x-dead-letter-routing-key. Pre-declare DLX and DLQ queues on broker.*
