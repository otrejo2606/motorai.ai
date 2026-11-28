package ai.motoria.vehicle.integration;

import ai.motoria.common.exception.DependencyUnavailableException;
import ai.motoria.vehicle.domain.VehicleAttributeKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapping of the Credid vehicle report (field names as consumed by the MNK WSO2 gateway). Fictitious data.
 */
class CredidVehicleRegistryAdapterTest {

    private static final String PLATE = "BCD123";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CredidVehicleRegistryAdapter adapter = new CredidVehicleRegistryAdapter(
            objectMapper, Optional.of("https://credid.example.test/api/Vehiculo"), Optional.of("test-token"),
            Duration.ofSeconds(5));

    @Test
    void foundVehicleShouldKeepTheRegistryValuesAndTheOwner() {
        VehicleRegistryRecord record = adapter.parse(200, """
                {"ConsultaId": 4321,
                 "Dato": {"Bien": "BCD-123", "Marca": "TOYOTA", "Modelo": "COROLLA", "Color": "GRIS", "Anio": 2019,
                   "TipoCategoria": "A", "NumeroVinCarroceria": "JTDBR32E720123456", "NumeroChasis": "CH-1",
                   "Traccion": "4X2", "Capacidad": 5, "ValorHacienda": 7500000, "PoseeGarantias": false,
                   "TotalPrenda": 0,
                   "Motor": {"Numero": "1ZZ1234567", "Marca": "TOYOTA", "Modelo": "1ZZ", "Cilindrada": 1800,
                     "Combustible": "GASOLINA", "Cilindros": 4, "Potencia": 130},
                   "InformacionPropietario": [
                     {"Nombre": "PROPIETARIO DE PRUEBA", "TipoIdentificacion": "CF", "Identificacion": "100000001"}]}}
                """, PLATE);

        assertTrue(record.found());
        assertEquals("TOYOTA", record.attributes().get(VehicleAttributeKey.BRAND));
        assertEquals("COROLLA", record.attributes().get(VehicleAttributeKey.MODEL));
        assertEquals("2019", record.attributes().get(VehicleAttributeKey.YEAR));
        assertEquals("GRIS", record.attributes().get(VehicleAttributeKey.COLOR));
        assertEquals("JTDBR32E720123456", record.attributes().get(VehicleAttributeKey.VIN));
        assertEquals("1ZZ1234567", record.attributes().get(VehicleAttributeKey.ENGINE_NUMBER));
        assertEquals("1800", record.attributes().get(VehicleAttributeKey.DISPLACEMENT_CC));
        assertEquals("GASOLINA", record.attributes().get(VehicleAttributeKey.FUEL));
        assertEquals("4X2", record.attributes().get(VehicleAttributeKey.DRIVETRAIN));
        assertEquals("5", record.attributes().get(VehicleAttributeKey.CAPACITY));
        assertFalse(record.attributes().containsKey(VehicleAttributeKey.OWNER_ID_HASH));
        assertFalse(record.pledged());
        assertEquals("PROPIETARIO DE PRUEBA", record.ownerName());
        assertEquals("100000001", record.ownerIdNumber());
        assertEquals("4321", record.providerReference());
        assertFalse(record.toString().contains("100000001"));
    }

    @Test
    void numbersAsTextAndChassisFallbackAreAccepted() {
        VehicleRegistryRecord record = adapter.parse(200, """
                {"Dato": {"Bien": "BCD123", "Marca": "NISSAN", "Anio": "2015.0", "NumeroChasis": "CHASIS123"}}
                """, PLATE);

        assertTrue(record.found());
        assertEquals("2015.0", record.attributes().get(VehicleAttributeKey.YEAR));
        assertEquals("CHASIS123", record.attributes().get(VehicleAttributeKey.VIN));
        assertNull(record.ownerIdNumber());
    }

    @Test
    void decimalYearIsReadWithoutTrailingZeros() {
        VehicleRegistryRecord record = adapter.parse(200, """
                {"Dato": {"Bien": "BCD123", "Marca": "NISSAN", "Anio": 2015.0}}
                """, PLATE);

        assertEquals("2015", record.attributes().get(VehicleAttributeKey.YEAR));
    }

    @Test
    void pledgeShouldBeReadFromItsVariants() throws Exception {
        assertTrue(pledgeOf(true, null));
        assertTrue(pledgeOf("S", null));
        assertTrue(pledgeOf("SI", null));
        assertTrue(pledgeOf("true", null));
        assertTrue(pledgeOf(1, null));
        assertTrue(pledgeOf("N", "1,500,000.00"));
        assertFalse(pledgeOf(false, null));
        assertFalse(pledgeOf("N", null));
        assertFalse(pledgeOf("NO", 0));
        assertFalse(pledgeOf(null, "sin dato"));
    }

    @Test
    void pledgeAmountIsKeptAsReceived() {
        VehicleRegistryRecord record = adapter.parse(200, """
                {"Dato": {"Bien": "BCD123", "Marca": "NISSAN", "PoseeGarantias": "S", "TotalPrenda": 2500000}}
                """, PLATE);

        assertTrue(record.pledged());
        assertEquals("2500000", record.pledgeAmount());
    }

    @Test
    void answerWithoutDatoOrWithoutPlateOrBrandMeansNotFound() {
        assertFalse(adapter.parse(200, """
                {"ConsultaId": 5}
                """, PLATE).found());
        assertFalse(adapter.parse(200, """
                {"Dato": null}
                """, PLATE).found());
        assertFalse(adapter.parse(200, """
                {"Dato": {"Marca": "TOYOTA"}}
                """, PLATE).found());
        assertFalse(adapter.parse(200, """
                {"Dato": {"Bien": "BCD123"}}
                """, PLATE).found());
        assertFalse(adapter.parse(200, "", PLATE).found());
    }

    @Test
    void notFoundStatusMeansNotFound() {
        assertFalse(adapter.parse(404, "", PLATE).found());
    }

    @Test
    void anotherPlateInTheAnswerMeansNotFound() {
        VehicleRegistryRecord record = adapter.parse(200, """
                {"Dato": {"Bien": "XYZ999", "Marca": "TOYOTA"}}
                """, PLATE);

        assertFalse(record.found());
    }

    @Test
    void plateWithoutClassPrefixIsAccepted() {
        VehicleRegistryRecord record = adapter.parse(200, """
                {"Dato": {"Bien": "123456", "Marca": "HINO"}}
                """, "CL123456");

        assertTrue(record.found());
    }

    @Test
    void rejectedCredentialsAndServerErrorsHaveStableCodes() {
        DependencyUnavailableException credentials = assertThrows(VehicleRegistryUnavailableException.class,
                () -> adapter.parse(401, "", PLATE));
        assertEquals("VEHICLE_REGISTRY_NOT_CONFIGURED", credentials.getCode());

        DependencyUnavailableException forbidden = assertThrows(VehicleRegistryUnavailableException.class,
                () -> adapter.parse(403, "", PLATE));
        assertEquals("VEHICLE_REGISTRY_NOT_CONFIGURED", forbidden.getCode());

        DependencyUnavailableException server = assertThrows(VehicleRegistryUnavailableException.class,
                () -> adapter.parse(500, "error", PLATE));
        assertEquals("VEHICLE_REGISTRY_UNAVAILABLE", server.getCode());

        DependencyUnavailableException unreadable = assertThrows(VehicleRegistryUnavailableException.class,
                () -> adapter.parse(200, "<html>", PLATE));
        assertEquals("VEHICLE_REGISTRY_UNAVAILABLE", unreadable.getCode());
    }

    @Test
    void missingUrlFailsWithoutCallingOut() {
        CredidVehicleRegistryAdapter unconfigured = new CredidVehicleRegistryAdapter(
                new ObjectMapper(), Optional.empty(), Optional.of("test-token"), Duration.ofSeconds(5));

        DependencyUnavailableException exception = assertThrows(VehicleRegistryUnavailableException.class,
                () -> unconfigured.lookup(PLATE));
        assertEquals("VEHICLE_REGISTRY_NOT_CONFIGURED", exception.getCode());
    }

    /**
     * Builds the answer with Jackson so each variant keeps its JSON type (boolean, number or text).
     */
    private boolean pledgeOf(Object poseeGarantias, Object totalPrenda) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode data = root.putObject("Dato");
        data.put("Bien", PLATE);
        data.put("Marca", "NISSAN");
        if (poseeGarantias != null) {
            data.set("PoseeGarantias", objectMapper.valueToTree(poseeGarantias));
        }
        if (totalPrenda != null) {
            data.set("TotalPrenda", objectMapper.valueToTree(totalPrenda));
        }
        return adapter.parse(200, objectMapper.writeValueAsString(root), PLATE).pledged();
    }
}
