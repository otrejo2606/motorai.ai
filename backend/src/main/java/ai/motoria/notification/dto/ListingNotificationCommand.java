package ai.motoria.notification.dto;

import ai.motoria.common.event.EventType;

import java.math.BigDecimal;
import java.util.UUID;

public record ListingNotificationCommand(
        EventType eventType,
        UUID correlationId,
        String sourceModule,
        UUID listingId,
        UUID sellerId,
        String listingStatus,
        BigDecimal price) {
}
