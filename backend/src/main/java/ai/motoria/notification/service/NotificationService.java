package ai.motoria.notification.service;

import ai.motoria.notification.dto.ListingNotificationCommand;
import ai.motoria.notification.dto.VehicleRegistryAnomalyNotificationCommand;
import ai.motoria.notification.dto.VehicleRegistryNotificationCommand;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotificationService {

    public void handleListingEvent(ListingNotificationCommand command) {
        Log.infov("Preparing notification for event {0} on listing {1}",
                command.eventType().routingKey(),
                command.listingId());
    }

    public void handleVehicleRegistryEvent(VehicleRegistryNotificationCommand command) {
        Log.infov("Preparing notification for event {0} on vehicle {1}",
                command.eventType().routingKey(),
                command.vehicleId());
    }

    /**
     * Registry anomaly for the seller (owner mismatch, pledge). Like the other notifications of F1 it is only prepared
     * here; the delivery channel (email or push) is still pending.
     */
    public void handleVehicleRegistryAnomaly(VehicleRegistryAnomalyNotificationCommand command) {
        Log.infov("Preparing registry anomaly notification for seller {0} on vehicle {1}: {2}",
                command.sellerId(),
                command.vehicleId(),
                String.join(",", command.anomalies()));
    }
}
