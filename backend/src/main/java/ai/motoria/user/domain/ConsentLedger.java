package ai.motoria.user.domain;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the current consent state from the append-only list of decisions.
 */
public final class ConsentLedger {

    private ConsentLedger() {
    }

    public static Map<ConsentType, Consent> current(List<Consent> decisions) {
        Map<ConsentType, Consent> current = new EnumMap<>(ConsentType.class);
        decisions.stream()
                .sorted(Comparator.comparing(Consent::getDecidedAt))
                .forEach(decision -> current.put(decision.getConsentType(), decision));
        return current;
    }

    public static boolean isGranted(List<Consent> decisions, ConsentType type) {
        Consent latest = current(decisions).get(type);
        return latest != null && latest.isGranted();
    }
}
