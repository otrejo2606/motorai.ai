package ai.motoria.certification.event;

import ai.motoria.certification.service.CertificationService;
import ai.motoria.common.domain.ListingStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CertificationEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final CertificationService certificationService = mock(CertificationService.class);
    private final CertificationEventConsumer consumer = new CertificationEventConsumer(objectMapper, certificationService);

    @Test
    void shouldDispatchCertificationRequestedEvent() {
        UUID correlationId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        consumer.onCertificationRequested("""
                {
                  "correlationId": "%s",
                  "eventType": "listing.certification.requested",
                  "sourceModule": "listing",
                  "timestamp": "2026-03-15T20:00:00Z",
                  "retryCount": 0,
                  "payload": {
                    "listingId": "%s",
                    "status": "PENDING_REVIEW"
                  }
                }
                """.formatted(correlationId, listingId));

        verify(certificationService).handleCertificationRequested(argThat(command ->
                command.correlationId().equals(correlationId)
                        && command.sourceModule().equals("listing")
                        && command.listingId().equals(listingId)
                        && command.listingStatus() == ListingStatus.PENDING_REVIEW));
    }

    @Test
    void shouldIgnoreWrongEventType() {
        consumer.onCertificationRequested("""
                {
                  "correlationId": "%s",
                  "eventType": "listing.created",
                  "sourceModule": "listing",
                  "timestamp": "2026-03-15T20:00:00Z",
                  "retryCount": 0,
                  "payload": {
                    "listingId": "%s",
                    "status": "PENDING_REVIEW"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(certificationService);
    }

    @Test
    void shouldRejectMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> consumer.onCertificationRequested("{invalid-json"));
        verifyNoInteractions(certificationService);
    }
}
