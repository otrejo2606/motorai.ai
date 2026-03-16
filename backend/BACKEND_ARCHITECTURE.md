# Motoria.ai — Backend Architecture

> Principal Architect Output
> Stack: Quarkus · PostgreSQL · RabbitMQ · Redis · S3 · WSO2

---

## 1. Architectural Principles

| Principle | Rule |
|---|---|
| Clean Architecture | Domain → Repository → Service → REST. No layer skipping. |
| Thin Controllers | REST resources only handle HTTP binding, delegation, and response shaping. |
| Business Logic Isolation | All business logic lives exclusively in services. |
| Persistence Isolation | Repositories contain only Panache/JPA calls. No business logic. |
| Integration Isolation | External system adapters live in `integration`. Never called directly from REST. |
| DTO/Entity Separation | MapStruct mappers perform all conversions. Entities never leave the service layer. |
| Event-Driven Async | Asynchronous flows use RabbitMQ. Services publish events; consumers react. |
| No Anemic Domain | Domain entities carry state and invariants. Business logic stays in services. |
| No God Classes | Each service has a single responsibility. Split by use case when needed. |

---

## 2. Global Package Root

```
ai.motoria.{module}
```

Every module is an independent package under the root. Modules do not import each other directly. Cross-module communication happens through events (RabbitMQ) or shared kernel types.

---

## 3. Module Layer Contract

Every module must contain exactly these sub-packages:

```
ai.motoria.{module}/
├── domain/          Entity, enum, value object — JPA-annotated, no DTOs
├── dto/             Request and Response records — no JPA annotations
├── repository/      PanacheRepository implementations — persistence only
├── service/         Business logic — orchestrates repo + integration + events
├── rest/            JAX-RS @Path resources — thin, delegates to service
├── mapper/          MapStruct interfaces — entity ↔ DTO conversion only
├── integration/     External adapter interfaces + implementations
├── event/           RabbitMQ producers and consumers
└── exception/       Module-specific checked and runtime exceptions
```

### Layer Rules

- `rest` → calls `service` only
- `service` → calls `repository`, `integration`, `event`
- `repository` → calls JPA/Panache only
- `mapper` → called by `service` before returning DTOs
- `integration` → wraps external HTTP/SDK calls
- `event` → publishes to or consumes from RabbitMQ
- `exception` → thrown by any layer, handled at `rest` via `ExceptionMapper`

---

## 4. Initial Modules

### 4.1 `listing`

Core domain of the marketplace. Manages the full lifecycle of a vehicle listing.

**Lifecycle states:** `DRAFT → PENDING_REVIEW → PUBLISHED → SOLD | EXPIRED | REJECTED`

```
ai.motoria.listing/
├── domain/
│   ├── Listing.java              (entity)
│   ├── ListingStatus.java        (enum)
│   ├── ListingCategory.java      (enum: CAR, MOTORCYCLE, TRUCK, VAN)
│   └── PriceRange.java           (value object)
├── dto/
│   ├── CreateListingRequest.java
│   ├── UpdateListingRequest.java
│   ├── ListingResponse.java
│   ├── ListingSummaryResponse.java
│   └── ListingSearchRequest.java
├── repository/
│   └── ListingRepository.java
├── service/
│   ├── ListingService.java
│   └── ListingSearchService.java
├── rest/
│   └── ListingResource.java      (@Path("/listings"))
├── mapper/
│   └── ListingMapper.java
├── integration/
│   └── AiPriceIntegration.java   (calls ai module service via internal port)
├── event/
│   ├── ListingEventProducer.java
│   └── ListingEventConsumer.java
└── exception/
    ├── ListingNotFoundException.java
    └── ListingInvalidStateException.java
```

**Key events produced:**
- `listing.created`
- `listing.updated`
- `listing.published`
- `listing.sold`
- `listing.certification.requested`

---

### 4.2 `vehicle`

Reference data for vehicle catalog. Makes, models, trims, specifications. Heavily cached in Redis.

```
ai.motoria.vehicle/
├── domain/
│   ├── Make.java
│   ├── Model.java
│   ├── Trim.java
│   └── VehicleSpec.java
├── dto/
│   ├── MakeResponse.java
│   ├── ModelResponse.java
│   └── VehicleSpecResponse.java
├── repository/
│   ├── MakeRepository.java
│   ├── ModelRepository.java
│   └── VehicleSpecRepository.java
├── service/
│   └── VehicleCatalogService.java
├── rest/
│   └── VehicleCatalogResource.java  (@Path("/vehicles/catalog"))
├── mapper/
│   └── VehicleCatalogMapper.java
├── integration/
│   └── ExternalVehicleDataIntegration.java  (e.g. VIN decoder API)
├── event/
│   └── VehicleCatalogEventConsumer.java
└── exception/
    └── VehicleNotFoundException.java
```

**Cache keys:**
- `vehicle:makes` — TTL 24h
- `vehicle:models:{makeId}` — TTL 24h
- `vehicle:spec:{specId}` — TTL 24h

---

### 4.3 `user`

User accounts, profiles, roles. Identity delegated to WSO2 Identity Server. Local mirror stores profile extensions.

**Roles:** `BUYER`, `SELLER`, `PARTNER`, `BACKOFFICE`, `ADMIN`

```
ai.motoria.user/
├── domain/
│   ├── UserProfile.java
│   ├── UserRole.java             (enum)
│   └── SellerProfile.java
├── dto/
│   ├── UserProfileResponse.java
│   ├── UpdateProfileRequest.java
│   └── SellerProfileResponse.java
├── repository/
│   ├── UserProfileRepository.java
│   └── SellerProfileRepository.java
├── service/
│   └── UserProfileService.java
├── rest/
│   └── UserProfileResource.java  (@Path("/users"))
├── mapper/
│   └── UserProfileMapper.java
├── integration/
│   └── Wso2IdentityIntegration.java  (SCIM2 / token introspection)
├── event/
│   └── UserEventConsumer.java
└── exception/
    ├── UserNotFoundException.java
    └── UnauthorizedProfileAccessException.java
```

---

### 4.4 `certification`

Vehicle certification workflow. A listing can request certification, which triggers an asynchronous review process.

**States:** `REQUESTED → IN_PROGRESS → APPROVED | REJECTED`

```
ai.motoria.certification/
├── domain/
│   ├── CertificationRequest.java
│   ├── CertificationStatus.java  (enum)
│   └── CertificationReport.java
├── dto/
│   ├── CertificationRequestDto.java
│   └── CertificationReportResponse.java
├── repository/
│   └── CertificationRepository.java
├── service/
│   └── CertificationService.java
├── rest/
│   └── CertificationResource.java  (@Path("/certifications"))
├── mapper/
│   └── CertificationMapper.java
├── integration/
│   └── CertificationPartnerIntegration.java
├── event/
│   ├── CertificationEventProducer.java
│   └── CertificationEventConsumer.java    (consumes listing.certification.requested)
└── exception/
    ├── CertificationNotFoundException.java
    └── CertificationAlreadyRequestedException.java
```

**Key events consumed:** `listing.certification.requested`
**Key events produced:** `listing.certification.completed`

---

### 4.5 `financing`

Financing simulation engine. Buyers request simulations; results are computed and returned synchronously or via event for heavy scenarios.

```
ai.motoria.financing/
├── domain/
│   ├── FinancingSimulation.java
│   ├── FinancingOffer.java
│   └── FinancingStatus.java      (enum)
├── dto/
│   ├── SimulationRequest.java
│   └── SimulationResponse.java
├── repository/
│   └── FinancingSimulationRepository.java
├── service/
│   └── FinancingSimulationService.java
├── rest/
│   └── FinancingResource.java    (@Path("/financing"))
├── mapper/
│   └── FinancingMapper.java
├── integration/
│   └── FinancingPartnerIntegration.java   (bank/credit partner APIs)
├── event/
│   ├── FinancingEventProducer.java
│   └── FinancingEventConsumer.java
└── exception/
    └── FinancingSimulationException.java
```

**Cache keys:**
- `financing:rates:{partnerId}` — TTL 1h

**Key events produced:** `finance.simulation.requested`, `finance.simulation.completed`

---

### 4.6 `inspection`

Inspection scheduling between buyers/sellers and partner garages. Manages time slots and reports.

**States:** `SCHEDULED → IN_PROGRESS → COMPLETED | CANCELLED`

```
ai.motoria.inspection/
├── domain/
│   ├── InspectionRequest.java
│   ├── InspectionSlot.java
│   ├── InspectionReport.java
│   └── InspectionStatus.java     (enum)
├── dto/
│   ├── ScheduleInspectionRequest.java
│   ├── InspectionResponse.java
│   └── InspectionReportResponse.java
├── repository/
│   ├── InspectionRepository.java
│   └── InspectionSlotRepository.java
├── service/
│   └── InspectionService.java
├── rest/
│   └── InspectionResource.java   (@Path("/inspections"))
├── mapper/
│   └── InspectionMapper.java
├── integration/
│   └── PartnerGarageIntegration.java
├── event/
│   ├── InspectionEventProducer.java
│   └── InspectionEventConsumer.java
└── exception/
    ├── InspectionNotFoundException.java
    └── SlotUnavailableException.java
```

**Key events produced:** `inspection.scheduled`, `inspection.completed`

---

### 4.7 `media`

Manages all file uploads (photos, documents). Stores to S3-compatible storage. Triggers AI photo analysis via event.

```
ai.motoria.media/
├── domain/
│   ├── MediaAsset.java
│   ├── MediaType.java            (enum: PHOTO, VIDEO, DOCUMENT)
│   └── MediaStatus.java          (enum: UPLOADED, ANALYZED, REJECTED)
├── dto/
│   ├── MediaUploadResponse.java
│   └── MediaAnalysisResponse.java
├── repository/
│   └── MediaAssetRepository.java
├── service/
│   └── MediaService.java
├── rest/
│   └── MediaResource.java        (@Path("/media"))
├── mapper/
│   └── MediaMapper.java
├── integration/
│   └── S3StorageIntegration.java
├── event/
│   ├── MediaEventProducer.java
│   └── MediaEventConsumer.java   (consumes ai.analysis.completed)
└── exception/
    ├── MediaUploadException.java
    └── InvalidFileTypeException.java
```

**Key events produced:** `media.uploaded`
**Key events consumed:** `ai.photo.analysis.completed`

---

### 4.8 `ai`

AI engine module. Seller assistant, buyer recommendations, pricing engine, photo damage detection, vehicle benchmark.

```
ai.motoria.ai/
├── domain/
│   ├── AiDecision.java
│   ├── AiDecisionType.java       (enum: PRICE, RECOMMENDATION, PHOTO_ANALYSIS, BENCHMARK)
│   └── AiAuditLog.java
├── dto/
│   ├── PriceRecommendationRequest.java
│   ├── PriceRecommendationResponse.java
│   ├── PhotoAnalysisRequest.java
│   ├── PhotoAnalysisResponse.java
│   ├── RecommendationRequest.java
│   └── RecommendationResponse.java
├── repository/
│   ├── AiDecisionRepository.java
│   └── AiAuditLogRepository.java
├── service/
│   ├── PriceRecommendationService.java
│   ├── PhotoAnalysisService.java
│   ├── BuyerRecommendationService.java
│   └── BenchmarkService.java
├── rest/
│   └── AiResource.java           (@Path("/ai"))
├── mapper/
│   └── AiMapper.java
├── integration/
│   ├── ClaudeAiIntegration.java   (Anthropic Claude API)
│   └── VisionModelIntegration.java
├── event/
│   ├── AiEventProducer.java
│   └── AiEventConsumer.java       (consumes media.uploaded for auto-analysis)
└── exception/
    ├── AiServiceException.java
    └── AiDecisionUnavailableException.java
```

**Cache keys:**
- `ai:price:{vehicleSpecId}:{year}:{mileage}` — TTL 30min

**Key events consumed:** `media.uploaded`
**Key events produced:** `ai.photo.analysis.completed`, `ai.price.recommended`

**Auditability:** Every AI decision is persisted in `AiAuditLog` with input, output, model version, and timestamp.

---

### 4.9 `partner`

Partner network management. Photography centers, garages, certification partners. Each partner has a profile, services offered, and availability calendar.

```
ai.motoria.partner/
├── domain/
│   ├── Partner.java
│   ├── PartnerType.java          (enum: PHOTOGRAPHY, GARAGE, CERTIFIER)
│   └── PartnerService.java
├── dto/
│   ├── PartnerResponse.java
│   ├── PartnerSummaryResponse.java
│   └── RegisterPartnerRequest.java
├── repository/
│   └── PartnerRepository.java
├── service/
│   └── PartnerService.java
├── rest/
│   └── PartnerResource.java      (@Path("/partners"))
├── mapper/
│   └── PartnerMapper.java
├── integration/
│   └── PartnerOnboardingIntegration.java
├── event/
│   └── PartnerEventProducer.java
└── exception/
    └── PartnerNotFoundException.java
```

---

### 4.10 `notification`

Event-driven notification engine. Consumes domain events and dispatches notifications via email, SMS, or push.

```
ai.motoria.notification/
├── domain/
│   ├── Notification.java
│   ├── NotificationChannel.java  (enum: EMAIL, SMS, PUSH)
│   └── NotificationStatus.java   (enum: PENDING, SENT, FAILED)
├── dto/
│   └── NotificationResponse.java
├── repository/
│   └── NotificationRepository.java
├── service/
│   └── NotificationDispatchService.java
├── rest/
│   └── NotificationResource.java  (@Path("/notifications"))
├── mapper/
│   └── NotificationMapper.java
├── integration/
│   ├── EmailIntegration.java      (SMTP / SendGrid)
│   ├── SmsIntegration.java        (Twilio / local gateway)
│   └── PushIntegration.java       (Firebase FCM)
├── event/
│   └── NotificationEventConsumer.java   (consumes all domain events)
└── exception/
    └── NotificationDeliveryException.java
```

**Key events consumed:** `listing.*`, `inspection.*`, `certification.*`, `finance.*`

---

### 4.11 `promotion`

Social media promotion for listings. Sellers request promotion; the platform posts to configured channels automatically.

```
ai.motoria.promotion/
├── domain/
│   ├── PromotionCampaign.java
│   ├── PromotionChannel.java     (enum: INSTAGRAM, FACEBOOK, WHATSAPP)
│   └── PromotionStatus.java      (enum: PENDING, ACTIVE, COMPLETED, FAILED)
├── dto/
│   ├── RequestPromotionRequest.java
│   └── PromotionCampaignResponse.java
├── repository/
│   └── PromotionCampaignRepository.java
├── service/
│   └── PromotionService.java
├── rest/
│   └── PromotionResource.java    (@Path("/promotions"))
├── mapper/
│   └── PromotionMapper.java
├── integration/
│   ├── InstagramIntegration.java
│   └── FacebookIntegration.java
├── event/
│   ├── PromotionEventProducer.java
│   └── PromotionEventConsumer.java   (consumes listing.published)
└── exception/
    └── PromotionFailedException.java
```

---

### 4.12 `subscription`

API monetization. Manages subscription plans, usage metering, and quota enforcement for external API consumers.

```
ai.motoria.subscription/
├── domain/
│   ├── SubscriptionPlan.java
│   ├── Subscription.java
│   └── ApiUsageRecord.java
├── dto/
│   ├── SubscriptionPlanResponse.java
│   ├── SubscribeRequest.java
│   └── ApiUsageResponse.java
├── repository/
│   ├── SubscriptionRepository.java
│   └── ApiUsageRepository.java
├── service/
│   └── SubscriptionService.java
├── rest/
│   └── SubscriptionResource.java   (@Path("/subscriptions"))
├── mapper/
│   └── SubscriptionMapper.java
├── integration/
│   └── Wso2ApiManagerIntegration.java   (application/subscription sync)
├── event/
│   └── SubscriptionEventProducer.java
└── exception/
    ├── SubscriptionNotFoundException.java
    └── QuotaExceededException.java
```

---

## 5. RabbitMQ Event Architecture

### Exchange Topology

| Exchange | Type | Purpose |
|---|---|---|
| `motoria.events` | topic | All domain events |
| `motoria.events.dlx` | direct | Dead letter routing |

### Event Envelope (all events)

```json
{
  "correlationId": "uuid-v4",
  "eventType": "listing.created",
  "sourceModule": "listing",
  "timestamp": "2026-03-15T10:00:00Z",
  "retryCount": 0,
  "payload": {}
}
```

### Event Catalog

| Routing Key | Producer | Consumers |
|---|---|---|
| `listing.created` | listing | notification, ai, promotion |
| `listing.updated` | listing | notification |
| `listing.published` | listing | notification, promotion |
| `listing.sold` | listing | notification, certification |
| `listing.certification.requested` | listing | certification |
| `listing.certification.completed` | certification | listing, notification |
| `finance.simulation.requested` | financing | financing |
| `finance.simulation.completed` | financing | notification |
| `inspection.scheduled` | inspection | notification |
| `inspection.completed` | inspection | listing, notification |
| `media.uploaded` | media | ai |
| `ai.photo.analysis.completed` | ai | media, listing |
| `ai.price.recommended` | ai | listing |
| `promotion.requested` | listing | promotion |
| `notification.send` | all | notification |

### Dead Letter Queue Strategy

Every queue declares:
```
x-dead-letter-exchange: motoria.events.dlx
x-dead-letter-routing-key: {queue-name}.dlq
x-message-ttl: 86400000   (24h before DLQ expiry)
```

Retry strategy: exponential backoff, max 3 retries. On third failure → DLQ.

---

## 6. Redis Cache Strategy

| Key Pattern | Module | TTL | Invalidation Trigger |
|---|---|---|---|
| `vehicle:makes` | vehicle | 24h | admin catalog update |
| `vehicle:models:{makeId}` | vehicle | 24h | admin catalog update |
| `vehicle:spec:{id}` | vehicle | 24h | admin catalog update |
| `financing:rates:{partnerId}` | financing | 1h | partner rate update |
| `ai:price:{specId}:{year}:{km}` | ai | 30min | new AI computation |
| `search:listings:{hashKey}` | listing | 5min | listing create/update |
| `ratelimit:{userId}:{endpoint}` | global | sliding window | automatic |

---

## 7. Security Architecture

### Identity & Access

- **Authentication:** WSO2 Identity Server — OAuth2 Authorization Code + PKCE
- **Token validation:** JWT introspection at WSO2 API Manager (gateway layer)
- **Authorization:** RBAC enforced at service layer with `@RolesAllowed`

### RBAC Roles

| Role | Permissions |
|---|---|
| `BUYER` | browse listings, request inspections, simulate financing |
| `SELLER` | manage own listings, request certification, request promotion |
| `PARTNER` | manage partner profile, accept inspections/certifications |
| `BACKOFFICE` | review listings, manage certifications, view reports |
| `ADMIN` | full platform access |

### OWASP Controls

| Control | Implementation |
|---|---|
| Input validation | Bean Validation `@Valid` on all REST DTOs |
| XSS protection | Response sanitization, Content-Security-Policy header |
| CSRF protection | SameSite cookies, CSRF token on state-changing operations |
| SQL injection | Panache parameterized queries only |
| File upload security | MIME type validation, size limit, virus scan hook |
| Rate limiting | Redis sliding window per user per endpoint |
| Audit logging | `AuditInterceptor` logs all write operations |
| Secure headers | Quarkus HTTP security headers filter |

---

## 8. PostgreSQL Schema Boundaries

Each module owns its own schema:

| Module | PostgreSQL Schema |
|---|---|
| listing | `listing` |
| vehicle | `vehicle` |
| user | `usr` |
| certification | `certification` |
| financing | `financing` |
| inspection | `inspection` |
| media | `media` |
| ai | `ai` |
| partner | `partner` |
| notification | `notification` |
| promotion | `promotion` |
| subscription | `subscription` |

Cross-schema foreign keys are avoided. References use IDs (UUIDs). Joins across modules happen at the service layer, not at the database level.

---

## 9. Anti-Patterns Explicitly Forbidden

| Anti-Pattern | Why | Enforcement |
|---|---|---|
| Fat controller | Breaks SRP, untestable | Code review — zero business logic in REST |
| Anemic service | DTOs passed directly to repo | Mapper enforced between service and REST |
| Cross-module direct DB access | Tight coupling | Separate schemas, no cross-schema queries |
| Shared mutable state | Concurrency bugs | Stateless services, Redis for shared state |
| God service | Impossible to maintain | One service per aggregate root |
| Synchronous integration chains | Cascading failures | Event-driven async for all cross-module flows |
| Entity exposure in REST | Leaks domain internals | MapStruct mapper mandatory on all responses |
| Magic strings for event types | Error-prone | `EventType` enum in shared kernel |

---

## 10. Quarkus Extensions Required

```xml
<!-- Core -->
quarkus-resteasy-reactive-jackson
quarkus-hibernate-orm-panache
quarkus-jdbc-postgresql
quarkus-redis-client
quarkus-smallrye-reactive-messaging-rabbitmq

<!-- Security -->
quarkus-oidc
quarkus-smallrye-jwt

<!-- Observability -->
quarkus-smallrye-openapi
quarkus-micrometer-registry-prometheus
quarkus-opentelemetry

<!-- Utilities -->
quarkus-mapstruct
quarkus-hibernate-validator
quarkus-cache
quarkus-s3  (Quarkus Amazon S3 extension)
```

---

## 11. Module Dependency Graph

```
REST layer (WSO2 API Manager)
         │
         ▼
  [listing] ──event──► [certification]
     │                       │
     │                       └──event──► [notification]
     │
     ├──event──► [ai] ──event──► [media]
     │                │
     │                └──event──► [listing] (price feedback)
     │
     ├──event──► [promotion]
     │
     ├──event──► [notification]
     │
[financing] ──event──► [notification]
[inspection] ──event──► [notification]
[media] ──event──► [ai]

[vehicle] ◄── cached ── [listing] (via Redis)
[user] ◄── internal ── [listing, financing, inspection]
[partner] ◄── internal ── [inspection, certification, promotion]
[subscription] ◄── WSO2 API Manager sync
```

---

*Generated by Architect Agent — Motoria.ai v1.0*
