package ai.motoria.vehicle.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Plate class rule of the MNK WSO2 gateway (design D6 of add-consulta-credid-personas).
 */
class CredidPlateClassTest {

    @Test
    void clPrefixIsClassClWithTheRest() {
        assertEquals(new CredidPlateClass("CL", "123456"), CredidPlateClass.of("CL123456"));
    }

    @Test
    void cPrefixIsClassCWithTheRest() {
        assertEquals(new CredidPlateClass("C", "123456"), CredidPlateClass.of("C123456"));
    }

    @Test
    void dFollowedByADigitIsClassDWithTheRest() {
        assertEquals(new CredidPlateClass("D", "12345"), CredidPlateClass.of("D12345"));
    }

    @Test
    void dFollowedByALetterIsClassAWithTheWholePlate() {
        assertEquals(new CredidPlateClass("A", "DBC123"), CredidPlateClass.of("DBC123"));
    }

    @Test
    void anythingElseIsClassAWithTheWholePlate() {
        assertEquals(new CredidPlateClass("A", "BCD123"), CredidPlateClass.of("BCD123"));
        assertEquals(new CredidPlateClass("A", "123456"), CredidPlateClass.of("123456"));
    }

    @Test
    void prefixWithoutNumberIsClassA() {
        assertEquals(new CredidPlateClass("A", "C"), CredidPlateClass.of("C"));
        assertEquals(new CredidPlateClass("A", "D"), CredidPlateClass.of("D"));
        assertEquals(new CredidPlateClass("A", ""), CredidPlateClass.of(null));
    }
}
