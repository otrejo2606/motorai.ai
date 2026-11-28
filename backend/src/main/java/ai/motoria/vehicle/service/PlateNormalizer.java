package ai.motoria.vehicle.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Plate normalization and validation per country. Formats come from configuration
 * (motoria.vehicle.plate-pattern.XX) and must match the whole normalized plate (upper case, letters and digits only).
 */
@ApplicationScoped
public class PlateNormalizer {

    @ConfigProperty(name = "motoria.vehicle.supported-countries", defaultValue = "CR")
    List<String> supportedCountries;

    private Map<String, Pattern> patterns = new HashMap<>();

    @PostConstruct
    void loadPatterns() {
        Map<String, Pattern> loaded = new HashMap<>();
        for (String country : supportedCountries) {
            String code = country.trim().toUpperCase(Locale.ROOT);
            ConfigProvider.getConfig()
                    .getOptionalValue("motoria.vehicle.plate-pattern." + code, String.class)
                    .ifPresent(pattern -> loaded.put(code, Pattern.compile(pattern)));
        }
        this.patterns = loaded;
    }

    /**
     * Factory for unit tests.
     */
    public static PlateNormalizer of(Map<String, String> patternsByCountry) {
        PlateNormalizer normalizer = new PlateNormalizer();
        Map<String, Pattern> compiled = new HashMap<>();
        patternsByCountry.forEach((country, pattern) -> compiled.put(country, Pattern.compile(pattern)));
        normalizer.patterns = compiled;
        return normalizer;
    }

    public boolean isSupported(String countryCode) {
        return countryCode != null && patterns.containsKey(countryCode.toUpperCase(Locale.ROOT));
    }

    public Optional<String> normalize(String countryCode, String plate) {
        if (!isSupported(countryCode) || plate == null) {
            return Optional.empty();
        }
        StringBuilder normalized = new StringBuilder();
        for (char character : plate.toUpperCase(Locale.ROOT).toCharArray()) {
            if ((character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9')) {
                normalized.append(character);
            }
        }
        String value = normalized.toString();
        Pattern pattern = patterns.get(countryCode.toUpperCase(Locale.ROOT));
        return !value.isEmpty() && pattern.matcher(value).matches() ? Optional.of(value) : Optional.empty();
    }
}
