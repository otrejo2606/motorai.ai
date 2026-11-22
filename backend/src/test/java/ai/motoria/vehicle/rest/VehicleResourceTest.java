package ai.motoria.vehicle.rest;

import ai.motoria.common.dto.PageResponse;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.port.OwnershipMatch;
import ai.motoria.vehicle.domain.VehicleStatus;
import ai.motoria.vehicle.dto.FeeResponse;
import ai.motoria.vehicle.dto.VehicleResponse;
import ai.motoria.vehicle.service.RegistryRequestService;
import ai.motoria.vehicle.service.RegistryReviewService;
import ai.motoria.vehicle.service.VehicleService;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Roles on the vehicle endpoints and the backoffice registry trays.
 */
@QuarkusTest
class VehicleResourceTest {

    @InjectMock
    VehicleService vehicleService;

    @InjectMock
    RegistryReviewService registryReviewService;

    @InjectMock
    RegistryRequestService registryRequestService;

    @InjectMock
    RedisDataSource redisDataSource;

    @InjectMock
    JsonWebToken jwt;

    @BeforeEach
    void setUp() {
        ValueCommands<String, Long> valueCommands = mock(ValueCommands.class);
        KeyCommands<String> keyCommands = mock(KeyCommands.class);
        when(redisDataSource.value(Long.class)).thenReturn(valueCommands);
        when(redisDataSource.key()).thenReturn(keyCommands);
        when(valueCommands.incr(anyString())).thenReturn(1L);
        when(jwt.getSubject()).thenReturn(null);
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void sellerCanRegisterAVehicle() {
        VehicleResponse response = vehicle();
        when(vehicleService.register(any())).thenReturn(response);

        given()
                .contentType("application/json")
                .body("""
                        {"countryCode": "CR", "plate": "BCD-123"}
                        """)
                .when()
                .post("/vehicles")
                .then()
                .statusCode(200)
                .body("status", equalTo("REGISTRY_PENDING"))
                .body("registryRequestFee.currency", equalTo("CRC"));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void invalidPlateShouldReturnStableCode() {
        when(vehicleService.register(any())).thenThrow(new InvalidRequestException("VEHICLE_PLATE_INVALID", "invalid"));

        given()
                .contentType("application/json")
                .body("""
                        {"countryCode": "CR", "plate": "@@@"}
                        """)
                .when()
                .post("/vehicles")
                .then()
                .statusCode(400)
                .body("code", equalTo("VEHICLE_PLATE_INVALID"));
    }

    @Test
    @TestSecurity(user = "buyer-1", roles = "BUYER")
    void buyerCannotRegisterVehicles() {
        given()
                .contentType("application/json")
                .body("""
                        {"countryCode": "CR", "plate": "BCD123"}
                        """)
                .when()
                .post("/vehicles")
                .then()
                .statusCode(403);
    }

    @Test
    void anonymousCannotReadVehicles() {
        given()
                .when()
                .get("/vehicles/%s".formatted(UUID.randomUUID()))
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void documentOfAnotherUserIsRejected() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleService.attachRegistryDocument(eq(vehicleId), any()))
                .thenThrow(new ForbiddenOperationException("VEHICLE_DOCUMENT_INVALID", "not yours"));

        given()
                .contentType("application/json")
                .body("""
                        {"mediaId": "%s"}
                        """.formatted(UUID.randomUUID()))
                .when()
                .post("/vehicles/%s/registry-documents".formatted(vehicleId))
                .then()
                .statusCode(403)
                .body("code", equalTo("VEHICLE_DOCUMENT_INVALID"));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void sellerCannotOpenTheRegistryReviewTray() {
        given()
                .when()
                .get("/backoffice/registry-reviews")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void analystCanListRegistryReviews() {
        when(registryReviewService.list(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

        given()
                .when()
                .get("/backoffice/registry-reviews?status=REGISTRY_IN_REVIEW")
                .then()
                .statusCode(200)
                .body("total", equalTo(0));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void sellerCannotApproveARegistryReview() {
        given()
                .when()
                .post("/backoffice/registry-reviews/%s/approve".formatted(UUID.randomUUID()))
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void sellerCannotOpenTheRegistryRequestTray() {
        given()
                .when()
                .get("/backoffice/registry-requests")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void analystCanListRegistryRequests() {
        when(registryRequestService.list(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

        given()
                .when()
                .get("/backoffice/registry-requests?status=PENDING")
                .then()
                .statusCode(200);
    }

    private VehicleResponse vehicle() {
        return new VehicleResponse(UUID.randomUUID(), "CR", "BCD123", VehicleStatus.REGISTRY_PENDING, null, null,
                false, null, null, List.of(), List.of(), new FeeResponse(BigDecimal.valueOf(5000), "CRC"), false,
                OwnershipMatch.UNKNOWN);
    }
}
