package ai.motoria.user.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryNameMatcherTest {

    @Test
    void shouldIgnoreCaseAccentsAndOrder() {
        assertTrue(RegistryNameMatcher.matches("María Pérez", "MARIA JOSE PEREZ ROJAS"));
        assertTrue(RegistryNameMatcher.matches("perez rojas, maria", "MARIA PEREZ ROJAS"));
    }

    @Test
    void shouldRejectAWordThatIsNotInTheRegistry() {
        assertFalse(RegistryNameMatcher.matches("Mario Perez", "MARIA PEREZ ROJAS"));
    }

    @Test
    void shouldRequireAtLeastTwoWords() {
        assertFalse(RegistryNameMatcher.matches("Maria", "MARIA PEREZ ROJAS"));
        assertFalse(RegistryNameMatcher.matches(null, "MARIA PEREZ ROJAS"));
        assertFalse(RegistryNameMatcher.matches("Maria Perez", null));
    }
}
