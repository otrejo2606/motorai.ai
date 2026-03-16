package ai.motoria.certification.event;

import ai.motoria.certification.dto.CertificationRequestedCommand;
import ai.motoria.certification.service.CertificationService;
import ai.motoria.common.event.DomainEventEnvelope;
import ai.motoria.common.event.EventType;
import ai.motoria.common.domain.ListingStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.UUID;

@ApplicationScoped
public class CertificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final CertificationService certificationService;

    @Inject
    public CertificationEventConsumer(ObjectMapper objectMapper, CertificationService certificationService) {
        this.objectMapper = objectMapper;
        this.certificationService = certificationService;
    }

    @Incoming("listing-certification-requested-in")
    @Blocking
    public void onCertificationRequested(String rawEvent) {
        DomainEventEnvelope<CertificationRequestedPayload> envelope = readEnvelope(rawEvent);
        if (!EventType.LISTING_CERTIFICATION_REQUESTED.routingKey().equals(envelope.eventType())) {
            Log.warnf("Ignoring unexpected certification event type %s", envelope.eventType());
            return;
        }

        CertificationRequestedPayload payload = envelope.payload();
        certificationService.handleCertificationRequested(new CertificationRequestedCommand(
                envelope.correlationId(),
                envelope.sourceModule(),
                payload.listingId(),
                payload.status()));
    }

    private DomainEventEnvelope<CertificationRequestedPayload> readEnvelope(String rawEvent) {
        try {
            return objectMapper.readValue(rawEvent, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize certification event", exception);
        }
    }

    private record CertificationRequestedPayload(
            UUID listingId,
            ListingStatus status) {
    }
}
