package ai.motoria.vehicle.service;

import ai.motoria.common.port.RegistryExtraction;
import ai.motoria.common.port.RegistryExtractionRequest;
import ai.motoria.vehicle.integration.RegistroNacionalPort;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.UUID;

/**
 * Background processing of vehicle.registry.requested: reads the document through the registry port and applies the
 * result. The model call runs outside any database transaction.
 */
@ApplicationScoped
public class RegistryProcessingService {

    @Inject
    RegistryExtractionApplier applier;

    @Inject
    RegistroNacionalPort registroNacionalPort;

    public void process(UUID vehicleId, UUID documentMediaId, UUID correlationId) {
        Optional<RegistryExtractionRequest> request = applier.prepare(vehicleId, documentMediaId, correlationId);
        if (request.isEmpty()) {
            Log.infov("Registry extraction skipped for vehicle {0}: not waiting for this document", vehicleId);
            return;
        }
        RegistryExtraction extraction = registroNacionalPort.read(request.get());
        applier.apply(vehicleId, documentMediaId, extraction);
    }
}
