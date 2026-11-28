package ai.motoria.user.service;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Compares the name typed in the profile with the name in the person registry, ignoring case, accents, punctuation
 * and word order. It matches when the profile has at least two words and every one of them is in the registry name
 * (the registry usually has both surnames and sometimes a second given name the user omits).
 */
public final class RegistryNameMatcher {

    private static final int MIN_WORDS = 2;

    private RegistryNameMatcher() {
    }

    public static boolean matches(String profileName, String registryName) {
        Set<String> profileWords = words(profileName);
        Set<String> registryWords = words(registryName);
        return profileWords.size() >= MIN_WORDS && registryWords.containsAll(profileWords);
    }

    static Set<String> words(String value) {
        Set<String> words = new LinkedHashSet<>();
        if (value == null) {
            return words;
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD).toUpperCase(Locale.ROOT);
        StringBuilder current = new StringBuilder();
        for (char character : decomposed.toCharArray()) {
            if (Character.getType(character) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(character)) {
                current.append(character);
            } else if (current.length() > 0) {
                words.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            words.add(current.toString());
        }
        return words;
    }
}
