package ai.motoria.user.integration;

import ai.motoria.user.domain.IdType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;

/**
 * Deterministic registry for dev and tests (motoria.user.person-registry.provider=stub). It never calls out:
 * a number ending in 0000 is not found, one ending in 9999 has an expired document, any other belongs to
 * motoria.user.person-registry.stub-full-name.
 */
@ApplicationScoped
@Typed(StubPersonRegistryAdapter.class)
public class StubPersonRegistryAdapter implements PersonRegistryPort {

    public static final String PROVIDER = "stub";

    private final String fullName;

    @Inject
    public StubPersonRegistryAdapter(
            @ConfigProperty(name = "motoria.user.person-registry.stub-full-name", defaultValue = "Persona de Prueba Motoria")
            String fullName) {
        this.fullName = fullName;
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public PersonRegistryRecord lookup(IdType idType, String idNumber) {
        if (idNumber.endsWith("0000")) {
            return PersonRegistryRecord.notFound("STUB");
        }
        LocalDate expiry = idNumber.endsWith("9999") ? LocalDate.now().minusDays(1) : LocalDate.now().plusYears(5);
        String personType = idType == IdType.RESIDENT_ID ? "E" : "F";
        return new PersonRegistryRecord(true, personType, fullName, expiry, "COSTA RICA", "STUB");
    }
}
