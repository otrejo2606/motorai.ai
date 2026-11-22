package ai.motoria.vehicle.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlateNormalizerTest {

    private final PlateNormalizer normalizer = PlateNormalizer.of(Map.of("CR", "([0-9]{1,6}|[A-Z]{1,3}[0-9]{1,6})"));

    @Test
    void shouldRemoveSeparatorsAndUpperCase() {
        assertEquals(Optional.of("BCD123"), normalizer.normalize("CR", "bcd-123"));
        assertEquals(Optional.of("CL123456"), normalizer.normalize("CR", "CL 123456"));
        assertEquals(Optional.of("123456"), normalizer.normalize("CR", "123456"));
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertTrue(normalizer.normalize("CR", "ABCD12345").isEmpty());
        assertTrue(normalizer.normalize("CR", "---").isEmpty());
        assertTrue(normalizer.normalize("CR", null).isEmpty());
    }

    @Test
    void shouldRejectUnsupportedCountry() {
        assertFalse(normalizer.isSupported("PA"));
        assertTrue(normalizer.normalize("PA", "ABC123").isEmpty());
    }
}
