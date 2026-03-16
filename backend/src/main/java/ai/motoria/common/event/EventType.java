package ai.motoria.common.event;

import java.util.Arrays;
import java.util.Optional;

public enum EventType {
    LISTING_CREATED("listing.created"),
    LISTING_UPDATED("listing.updated"),
    LISTING_PUBLISHED("listing.published"),
    LISTING_SOLD("listing.sold"),
    LISTING_CERTIFICATION_REQUESTED("listing.certification.requested"),
    LISTING_CERTIFICATION_COMPLETED("listing.certification.completed"),
    FINANCE_SIMULATION_REQUESTED("finance.simulation.requested"),
    FINANCE_SIMULATION_COMPLETED("finance.simulation.completed"),
    INSPECTION_SCHEDULED("inspection.scheduled"),
    INSPECTION_COMPLETED("inspection.completed"),
    MEDIA_UPLOADED("media.uploaded"),
    AI_PHOTO_ANALYSIS_COMPLETED("ai.photo.analysis.completed"),
    AI_PRICE_RECOMMENDED("ai.price.recommended"),
    PROMOTION_REQUESTED("promotion.requested"),
    NOTIFICATION_SEND("notification.send");

    private final String routingKey;

    EventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }

    public static Optional<EventType> fromRoutingKey(String routingKey) {
        return Arrays.stream(values())
                .filter(eventType -> eventType.routingKey.equals(routingKey))
                .findFirst();
    }
}
