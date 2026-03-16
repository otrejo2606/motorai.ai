package ai.motoria.notification.service;

import ai.motoria.notification.dto.ListingNotificationCommand;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotificationService {

    public void handleListingEvent(ListingNotificationCommand command) {
        Log.infov("Preparing notification for event {0} on listing {1}",
                command.eventType().routingKey(),
                command.listingId());
    }
}
