package ai.motoria.vehicle.integration;

import ai.motoria.common.port.RegistryDocumentReaderPort;
import ai.motoria.common.port.RegistryExtraction;
import ai.motoria.common.port.RegistryExtractionRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Registry data from a document supplied by the seller or an operator (PDF from rnpdigital.com), read by the ai
 * module through the shared {@link RegistryDocumentReaderPort}.
 */
@ApplicationScoped
public class RegistroDocumentoAdapter implements RegistroNacionalPort {

    @Inject
    RegistryDocumentReaderPort registryDocumentReader;

    @Override
    public RegistryExtraction read(RegistryExtractionRequest request) {
        return registryDocumentReader.extract(request);
    }
}
