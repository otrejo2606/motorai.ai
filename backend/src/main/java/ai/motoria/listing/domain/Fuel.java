package ai.motoria.listing.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Design D9.1 catalog. The registry writes the fuel in Spanish; {@link #fromRegistry(String)} maps it.
 */
public enum Fuel {
    GASOLINE,
    DIESEL,
    HYBRID,
    PLUG_IN_HYBRID,
    ELECTRIC,
    LPG,
    OTHER;

    public static Fuel fromRegistry(String registryValue) {
        if (registryValue == null || registryValue.isBlank()) {
            return null;
        }
        String value = Normalizer.normalize(registryValue, Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z ]", "")
                .toUpperCase(Locale.ROOT)
                .trim();
        if (value.contains("ENCHUF") || value.contains("PLUG")) {
            return PLUG_IN_HYBRID;
        }
        if (value.contains("HIBRID") || value.contains("HYBRID")) {
            return HYBRID;
        }
        if (value.contains("ELECTRIC")) {
            return ELECTRIC;
        }
        if (value.contains("DIESEL")) {
            return DIESEL;
        }
        if (value.contains("GLP") || value.contains("LPG") || value.contains("GAS LP")) {
            return LPG;
        }
        if (value.contains("GASOLIN") || value.contains("GASOLINE")) {
            return GASOLINE;
        }
        return OTHER;
    }
}
