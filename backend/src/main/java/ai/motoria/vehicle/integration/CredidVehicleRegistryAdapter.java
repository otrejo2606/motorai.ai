package ai.motoria.vehicle.integration;

import ai.motoria.vehicle.domain.VehicleAttributeKey;
import ai.motoria.vehicle.exception.VehicleErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Credid vehicle report (contract taken from the MNK WSO2 gateway, sequences QueryCredid and QueryCredidCalidad):
 * GET {url}?placa={number}&clase={class} with the token as is in the Authorization header (no "Bearer" prefix), the
 * same token as the person report. The answer carries a "Dato" object; the field types are not confirmed, so every
 * value is read leniently (text or number, PoseeGarantias as boolean or text). Only the values Motoria shows or needs
 * are kept: no fiscal value, no power, no cylinders. The plate and the owner identity number are never logged.
 */
@ApplicationScoped
@Typed(CredidVehicleRegistryAdapter.class)
public class CredidVehicleRegistryAdapter implements VehicleRegistryLookupPort {

    public static final String PROVIDER = "credid";
    private static final int MAX_VALUE_LENGTH = 500;
    private static final Set<String> TRUE_FLAGS = Set.of("S", "SI", "TRUE", "Y", "YES", "1");

    private final ObjectMapper objectMapper;
    private final Optional<String> url;
    private final Optional<String> token;
    private final Duration timeout;
    private final HttpClient httpClient;

    @Inject
    public CredidVehicleRegistryAdapter(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "motoria.vehicle.registry.credid.url") Optional<String> url,
            @ConfigProperty(name = "motoria.user.person-registry.credid.token") Optional<String> token,
            @ConfigProperty(name = "motoria.vehicle.registry.credid.timeout", defaultValue = "PT15S") Duration timeout) {
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
    public VehicleRegistryRecord lookup(String normalizedPlate) {
        String base = url.filter(value -> !value.isBlank())
                .orElseThrow(() -> new VehicleRegistryUnavailableException(VehicleErrorCodes.VEHICLE_REGISTRY_NOT_CONFIGURED,
                        "The vehicle registry URL is not configured"));
        String key = token.filter(value -> !value.isBlank())
                .orElseThrow(() -> new VehicleRegistryUnavailableException(VehicleErrorCodes.VEHICLE_REGISTRY_NOT_CONFIGURED,
                        "The vehicle registry token is not configured"));
        CredidPlateClass plateClass = CredidPlateClass.of(normalizedPlate);
        String separator = base.contains("?") ? "&" : "?";
        URI endpoint;
        try {
            endpoint = URI.create(base.trim() + separator
                    + "placa=" + URLEncoder.encode(plateClass.number(), StandardCharsets.UTF_8)
                    + "&clase=" + URLEncoder.encode(plateClass.plateClass(), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new VehicleRegistryUnavailableException(VehicleErrorCodes.VEHICLE_REGISTRY_NOT_CONFIGURED,
                    "The vehicle registry URL is not valid", exception);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("Authorization", key.trim())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parse(response.statusCode(), response.body(), normalizedPlate);
        } catch (IOException | IllegalArgumentException exception) {
            throw new VehicleRegistryUnavailableException(VehicleErrorCodes.VEHICLE_REGISTRY_UNAVAILABLE,
                    "The vehicle registry is not reachable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VehicleRegistryUnavailableException(VehicleErrorCodes.VEHICLE_REGISTRY_UNAVAILABLE,
                    "The vehicle registry call was interrupted", exception);
        }
    }

    /**
     * Maps the HTTP answer. 404, an answer without "Dato", a "Dato" without Bien or Marca, or a Bien that is not the
     * plate that was asked for means the plate is not in the registry.
     */
    VehicleRegistryRecord parse(int statusCode, String body, String normalizedPlate) {
        if (statusCode == 404) {
            return VehicleRegistryRecord.notFound(null);
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new VehicleRegistryUnavailableException(VehicleErrorCodes.VEHICLE_REGISTRY_NOT_CONFIGURED,
                    "The vehicle registry rejected the credentials (status " + statusCode + ")");
        }
        if (statusCode < 200 || statusCode >= 300) {
            throw new VehicleRegistryUnavailableException(VehicleErrorCodes.VEHICLE_REGISTRY_UNAVAILABLE,
                    "The vehicle registry returned status " + statusCode);
        }
        if (body == null || body.isBlank()) {
            return VehicleRegistryRecord.notFound(null);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new VehicleRegistryUnavailableException(VehicleErrorCodes.VEHICLE_REGISTRY_UNAVAILABLE,
                    "The vehicle registry returned an unreadable answer", exception);
        }
        if (root == null || !root.isObject()) {
            throw new VehicleRegistryUnavailableException(VehicleErrorCodes.VEHICLE_REGISTRY_UNAVAILABLE,
                    "The vehicle registry returned an unexpected answer");
        }
        JsonNode data = root.path("Dato");
        if (data.isArray()) {
            data = data.path(0);
        }
        String reference = firstNonBlank(text(root.path("ConsultaId")), text(data.path("ConsultaId")));
        if (!data.isObject()) {
            return VehicleRegistryRecord.notFound(reference);
        }
        String plate = text(data.path("Bien"));
        String brand = text(data.path("Marca"));
        if (plate == null || brand == null || !samePlate(plate, normalizedPlate)) {
            return VehicleRegistryRecord.notFound(reference);
        }

        JsonNode engine = data.path("Motor");
        Map<VehicleAttributeKey, String> attributes = new EnumMap<>(VehicleAttributeKey.class);
        put(attributes, VehicleAttributeKey.BRAND, brand);
        put(attributes, VehicleAttributeKey.MODEL, text(data.path("Modelo")));
        put(attributes, VehicleAttributeKey.YEAR, text(data.path("Anio")));
        put(attributes, VehicleAttributeKey.COLOR, text(data.path("Color")));
        put(attributes, VehicleAttributeKey.VIN,
                firstNonBlank(text(data.path("NumeroVinCarroceria")), text(data.path("NumeroChasis"))));
        put(attributes, VehicleAttributeKey.ENGINE_NUMBER, text(engine.path("Numero")));
        put(attributes, VehicleAttributeKey.DISPLACEMENT_CC, text(engine.path("Cilindrada")));
        put(attributes, VehicleAttributeKey.FUEL, text(engine.path("Combustible")));
        put(attributes, VehicleAttributeKey.DRIVETRAIN, text(data.path("Traccion")));
        put(attributes, VehicleAttributeKey.CAPACITY, text(data.path("Capacidad")));

        String pledgeAmount = text(data.path("TotalPrenda"));
        boolean pledged = isTrue(data.path("PoseeGarantias")) || isPositive(pledgeAmount);

        JsonNode owners = data.path("InformacionPropietario");
        JsonNode owner = owners.isArray() ? owners.path(0) : owners;
        String ownerName = owner.isObject() ? text(owner.path("Nombre")) : null;
        String ownerIdNumber = owner.isObject() ? text(owner.path("Identificacion")) : null;

        return new VehicleRegistryRecord(true, attributes, pledged, pledgeAmount, ownerName, ownerIdNumber, reference);
    }

    /**
     * Bien may come with dashes or spaces, and for classes CL, C and D possibly without the class prefix.
     */
    static boolean samePlate(String registryPlate, String normalizedPlate) {
        if (normalizedPlate == null) {
            return false;
        }
        String received = normalize(registryPlate);
        return received.equals(normalizedPlate) || received.equals(CredidPlateClass.of(normalizedPlate).number());
    }

    static boolean isTrue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.decimalValue().signum() != 0;
        }
        return TRUE_FLAGS.contains(normalize(node.asText()));
    }

    static boolean isPositive(String amount) {
        if (amount == null) {
            return false;
        }
        try {
            return new BigDecimal(amount.replace(",", "").trim()).signum() > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static void put(Map<VehicleAttributeKey, String> attributes, VehicleAttributeKey key, String value) {
        if (value != null) {
            attributes.put(key, value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH));
        }
    }

    /**
     * Upper case letters and digits only (accented letters and separators are dropped, so an accented "SI" reads as "S").
     */
    private static String normalize(String value) {
        StringBuilder normalized = new StringBuilder();
        for (char character : value.toUpperCase(Locale.ROOT).toCharArray()) {
            if ((character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9')) {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    private static String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.isContainerNode()) {
            return null;
        }
        String value;
        if (node.isNumber() && !node.isIntegralNumber()) {
            value = node.decimalValue().stripTrailingZeros().toPlainString();
        } else {
            value = node.asText();
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }
}
