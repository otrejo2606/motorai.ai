package ai.motoria.certification.service;

import ai.motoria.certification.dto.CertificationRequestedCommand;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CertificationService {

    public void handleCertificationRequested(CertificationRequestedCommand command) {
        Log.infov("Received certification request for listing {0} with correlation {1}",
                command.listingId(),
                command.correlationId());
    }
}
