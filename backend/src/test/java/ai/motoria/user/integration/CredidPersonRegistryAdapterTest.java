package ai.motoria.user.integration;

import ai.motoria.common.exception.DependencyUnavailableException;
import ai.motoria.user.domain.IdType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapping of the Credid person report (field names as consumed by the MNK Acsel gateway). Fictitious data.
 */
class CredidPersonRegistryAdapterTest {

    private final CredidPersonRegistryAdapter adapter = new CredidPersonRegistryAdapter(
            new ObjectMapper(), Optional.of("https://credid.example.test/api/Reporte"), Optional.of("test-token"),
            Duration.ofSeconds(5));

    @Test
    void physicalPersonShouldKeepNameExpiryAndReference() {
        PersonRegistryRecord record = adapter.parse(200, """
                {"Tipo": "Fisica", "ConsultaId": 98765,
                 "FiliacionFisica": {"Identificacion": "102340567", "Nombre": "MARIA JOSE", "Apellido1": "PEREZ",
                   "Apellido2": "ROJAS", "VencimientoCedula": "2031-05-12T00:00:00", "Nacionalidad": "COSTARRICENSE"},
                 "Localizacion": [{"Tipo": "TELEFONO", "Dato": "88888888"}]}
                """);

        assertTrue(record.found());
        assertEquals("F", record.personType());
        assertEquals("MARIA JOSE PEREZ ROJAS", record.fullName());
        assertEquals(LocalDate.of(2031, 5, 12), record.documentExpiresOn());
        assertEquals("COSTARRICENSE", record.nationality());
        assertEquals("98765", record.providerReference());
    }

    @Test
    void foreignerShouldUseTheDocumentExpiry() {
        PersonRegistryRecord record = adapter.parse(200, """
                {"ConsultaId": 1, "FiliacionExtranjero": {"Identificacion": "155812345678", "Nombre": "ANA",
                 "Apellido1": "GOMEZ", "FechaVencimientoDocumento": "01/02/2027", "Pais": "NICARAGUA"}}
                """);

        assertTrue(record.found());
        assertEquals("E", record.personType());
        assertEquals("ANA GOMEZ", record.fullName());
        assertEquals(LocalDate.of(2027, 2, 1), record.documentExpiresOn());
        assertEquals("NICARAGUA", record.nationality());
    }

    @Test
    void legalPersonShouldHaveNoDocumentExpiry() {
        PersonRegistryRecord record = adapter.parse(200, """
                {"FiliacionJuridica": {"Identificacion": "3101123456", "Nombre": "AUTOS DE PRUEBA SOCIEDAD ANONIMA"}}
                """);

        assertTrue(record.found());
        assertEquals("J", record.personType());
        assertNull(record.documentExpiresOn());
    }

    @Test
    void answerWithoutFiliationOrNotFoundStatusMeansNotFound() {
        assertFalse(adapter.parse(200, """
                {"ConsultaId": 5, "FiliacionFisica": null}
                """).found());
        assertFalse(adapter.parse(404, "").found());
        assertFalse(adapter.parse(200, "").found());
    }

    @Test
    void rejectedCredentialsAndServerErrorsHaveStableCodes() {
        DependencyUnavailableException credentials = assertThrows(PersonRegistryUnavailableException.class,
                () -> adapter.parse(401, ""));
        assertEquals("PERSON_REGISTRY_NOT_CONFIGURED", credentials.getCode());

        DependencyUnavailableException server = assertThrows(PersonRegistryUnavailableException.class,
                () -> adapter.parse(500, "error"));
        assertEquals("PERSON_REGISTRY_UNAVAILABLE", server.getCode());

        DependencyUnavailableException unreadable = assertThrows(PersonRegistryUnavailableException.class,
                () -> adapter.parse(200, "<html>"));
        assertEquals("PERSON_REGISTRY_UNAVAILABLE", unreadable.getCode());
    }

    @Test
    void missingConfigurationFailsWithoutCallingOut() {
        CredidPersonRegistryAdapter unconfigured = new CredidPersonRegistryAdapter(
                new ObjectMapper(), Optional.empty(), Optional.empty(), Duration.ofSeconds(5));

        DependencyUnavailableException exception = assertThrows(PersonRegistryUnavailableException.class,
                () -> unconfigured.lookup(IdType.NATIONAL_ID, "102340567"));
        assertEquals("PERSON_REGISTRY_NOT_CONFIGURED", exception.getCode());
    }

    @Test
    void datesInUnknownFormatsAreIgnored() {
        assertNull(CredidPersonRegistryAdapter.parseDate("12-2031"));
        assertNull(CredidPersonRegistryAdapter.parseDate("31/02/2031"));
        assertEquals(LocalDate.of(2031, 5, 12), CredidPersonRegistryAdapter.parseDate("2031-05-12"));
    }
}
