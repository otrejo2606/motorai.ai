package ai.motoria.vehicle.integration;

import ai.motoria.common.port.RegistryExtraction;
import ai.motoria.common.port.RegistryExtractionRequest;

/**
 * Source of registry data, interchangeable by configuration (base requirement "Fuentes intercambiables y sin
 * evasion"). F1 has only the document adapter; the agreement adapter arrives in F7. No adapter may automate public
 * queries protected by captcha.
 */
public interface RegistroNacionalPort {

    RegistryExtraction read(RegistryExtractionRequest request);
}
