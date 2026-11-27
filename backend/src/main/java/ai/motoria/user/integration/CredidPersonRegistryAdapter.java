package ai.motoria.user.integration;

import ai.motoria.user.domain.IdType;
import ai.motoria.user.exception.UserErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Credid person report (contract taken from the MNK Acsel gateway that consumes it in production):
 * GET {url}?cedula={number} with the token as is in the Authorization header (no "Bearer" prefix).
 * The answer carries FiliacionFisica, FiliacionJuridica or FiliacionExtranjero; only the name, the document expiry,
 * the nationality and ConsultaId are kept. The identity number is never logged.
 */
@ApplicationScoped
@Typed(CredidPersonRegistryAdapter.class)
public class CredidPersonRegistryAdapter implements PersonRegistryPort {

    public static final String PROVIDER = "credid";
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private final ObjectMapper objectMapper;
    private final Optional<String> url;
    private final Optional<String> token;
    private final Duration timeout;
    private final HttpClient httpClient;

    @Inject
    public CredidPersonRegistryAdapter(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "motoria.user.person-registry.credid.url") Optional<String> url,
            @ConfigProperty(name = "motoria.user.person-registry.credid.token") Optional<String> token,
            @ConfigProperty(name = "motoria.user.person-registry.timeout", defaultValue = "PT15S") Duration timeout) {
        this.objectMapper = objectMapper;
        this.url = url;
        this.token = token;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public PersonRegistryRecord lookup(IdType idType, String idNumber) {
        String base = url.filter(value -> !value.isBlank())
                .orElseThrow(() -> new PersonRegistryUnavailableException(UserErrorCodes.PERSON_REGISTRY_NOT_CONFIGURED,
                        "The person registry URL is not configured"));
        String key = token.filter(value -> !value.isBlank())
                .orElseThrow(() -> new PersonRegistryUnavailableException(UserErrorCodes.PERSON_REGISTRY_NOT_CONFIGURED,
                        "The person registry token is not configured"));
        String separator = base.contains("?") ? "&" : "?";
        URI endpoint;
        try {
            endpoint = URI.create(base.trim() + separator + "cedula=" + URLEncoder.encode(idNumber, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new PersonRegistryUnavailableException(UserErrorCodes.PERSON_REGISTRY_NOT_CONFIGURED,
                    "The person registry URL is not valid", exception);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("Authorization", key.trim())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parse(response.statusCode(), response.body());
        } catch (IOException | IllegalArgumentException exception) {
            throw new PersonRegistryUnavailableException(UserErrorCodes.PERSON_REGISTRY_UNAVAILABLE,
                    "The person registry is not reachable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PersonRegistryUnavailableException(UserErrorCodes.PERSON_REGISTRY_UNAVAILABLE,
                    "The person registry call was interrupted", exception);
        }
    }

    /**
     * Maps the HTTP answer. 404 or an answer without any filiation means the document is not in the registry.
     */
    PersonRegistryRecord parse(int statusCode, String body) {
        if (statusCode == 404) {
            return PersonRegistryRecord.notFound(null);
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new PersonRegistryUnavailableException(UserErrorCodes.PERSON_REGISTRY_NOT_CONFIGURED,
                    "The person registry rejected the credentials (status " + statusCode + ")");
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new PersonRegistryUnavailableException(UserErrorCodes.PERSON_REGISTRY_UNAVAILABLE,
                    "The person registry returned status " + statusCode);
        }
        if (body == null || body.isBlank()) {
            return PersonRegistryRecord.notFound(null);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new PersonRegistryUnavailableException(UserErrorCodes.PERSON_REGISTRY_UNAVAILABLE,
                    "The person registry returned an unreadable answer", exception);
        }
        String reference = text(root.path("ConsultaId"));

        JsonNode physical = root.path("FiliacionFisica");
        if (hasPerson(physical)) {
            return new PersonRegistryRecord(true, "F",
                    joinName(physical),
                    parseDate(text(physical.path("VencimientoCedula"))),
                    firstNonBlank(text(physical.path("Nacionalidad")), text(physical.path("Pais"))),
                    reference);
        }
        JsonNode foreigner = root.path("FiliacionExtranjero");
        if (hasPerson(foreigner)) {
            return new PersonRegistryRecord(true, "E",
                    joinName(foreigner),
                    parseDate(text(foreigner.path("FechaVencimientoDocumento"))),
                    firstNonBlank(text(foreigner.path("Nacionalidad")), text(foreigner.path("Pais"))),
                    reference);
        }
        JsonNode legal = root.path("FiliacionJuridica");
        if (hasPerson(legal)) {
            return new PersonRegistryRecord(true, "J",
                    text(legal.path("Nombre")),
                    null,
                    text(legal.path("Pais")),
                    reference);
        }
        return PersonRegistryRecord.notFound(reference);
    }

    private static boolean hasPerson(JsonNode node) {
        return node.isObject() && (text(node.path("Identificacion")) != null || text(node.path("Nombre")) != null);
    }

    private static String joinName(JsonNode node) {
        List<String> parts = new ArrayList<>();
        for (String field : List.of("Nombre", "Apellido1", "Apellido2")) {
            String value = text(node.path(field));
            if (value != null) {
                parts.add(value);
            }
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    /**
     * Accepts ISO dates (with or without time) and dd/MM/yyyy; anything else is treated as unknown.
     */
    static LocalDate parseDate(String raw) {
        if (raw == null || raw.length() < 10) {
            return null;
        }
        String day = raw.substring(0, 10);
        try {
            if (day.charAt(4) == '-') {
                return LocalDate.parse(day);
            }
            if (day.charAt(2) == '/') {
                return LocalDate.parse(day, DAY_MONTH_YEAR);
            }
        } catch (DateTimeParseException exception) {
            return null;
        }
        return null;
    }
}
