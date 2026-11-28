package ai.motoria.listing.rest;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.common.dto.PageResponse;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.listing.domain.PhotoAngleStatus;
import ai.motoria.listing.dto.PhotoReviewItemResponse;
import ai.motoria.listing.exception.ListingErrorCodes;
import ai.motoria.listing.service.ListingPhotoReviewService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Roles and validation of the analyst tray of the photo angle check (add-captura-guiada-fotos).
 */
@QuarkusTest
class BackofficePhotoReviewResourceTest {

    @InjectMock
    ListingPhotoReviewService listingPhotoReviewService;

    @InjectMock
    RedisDataSource redisDataSource;

    @InjectMock
    JsonWebToken jwt;

    ValueCommands<String, Long> valueCommands;
    KeyCommands<String> keyCommands;

    @BeforeEach
    void setUp() {
        valueCommands = mock(ValueCommands.class);
        keyCommands = mock(KeyCommands.class);
        when(redisDataSource.value(Long.class)).thenReturn(valueCommands);
        when(redisDataSource.key()).thenReturn(keyCommands);
        when(valueCommands.incr(anyString())).thenReturn(1L);
        when(jwt.getSubject()).thenReturn(null);
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void analystShouldSeeTheTray() {
        when(listingPhotoReviewService.pending(anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(item()), 0, 20, 1));

        given()
                .when()
                .get("/backoffice/photo-reviews")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].slot", equalTo("FRONT"))
                .body("items[0].angleStatus", equalTo("UNCERTAIN"));
    }

    @Test
    @TestSecurity(user = "admin-1", roles = "ADMIN")
    void adminShouldDecideOnAPhoto() {
        UUID listingId = UUID.randomUUID();
        when(listingPhotoReviewService.review(eq(listingId), eq("FRONT"), any())).thenReturn(item());

        given()
                .contentType("application/json")
                .body("""
                        {"decision": "ACCEPTED"}
                        """)
                .when()
                .post("/backoffice/photo-reviews/%s/FRONT".formatted(listingId))
                .then()
                .statusCode(200)
                .body("slot", equalTo("FRONT"));
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void decisionIsRequired() {
        given()
                .contentType("application/json")
                .body("""
                        {"reason": "BLURRY"}
                        """)
                .when()
                .post("/backoffice/photo-reviews/%s/FRONT".formatted(UUID.randomUUID()))
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));

        verify(listingPhotoReviewService, never()).review(any(), any(), any());
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void selfReviewShouldBeForbidden() {
        UUID listingId = UUID.randomUUID();
        when(listingPhotoReviewService.review(eq(listingId), eq("FRONT"), any()))
                .thenThrow(new ForbiddenOperationException(ListingErrorCodes.LISTING_PHOTO_SELF_REVIEW_FORBIDDEN,
                        "An analyst cannot review the photos of a listing they sell"));

        given()
                .contentType("application/json")
                .body("""
                        {"decision": "REJECTED", "reason": "BLURRY"}
                        """)
                .when()
                .post("/backoffice/photo-reviews/%s/FRONT".formatted(listingId))
                .then()
                .statusCode(403)
                .body("code", equalTo("LISTING_PHOTO_SELF_REVIEW_FORBIDDEN"));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void sellerCannotUseTheTray() {
        given()
                .when()
                .get("/backoffice/photo-reviews")
                .then()
                .statusCode(403);
        given()
                .contentType("application/json")
                .body("""
                        {"decision": "ACCEPTED"}
                        """)
                .when()
                .post("/backoffice/photo-reviews/%s/FRONT".formatted(UUID.randomUUID()))
                .then()
                .statusCode(403);
    }

    @Test
    void anonymousCannotUseTheTray() {
        given()
                .when()
                .get("/backoffice/photo-reviews")
                .then()
                .statusCode(401);
    }

    private PhotoReviewItemResponse item() {
        return new PhotoReviewItemResponse(
                UUID.randomUUID(),
                ListingStatus.PENDING_REVIEW,
                UUID.randomUUID(),
                40000,
                "FRONT",
                UUID.randomUUID(),
                "https://cdn.example/1.jpg",
                PhotoAngleStatus.UNCERTAIN,
                "FRONT_LEFT",
                new BigDecimal("0.7000"),
                null,
                "angle-cls-1",
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
    }
}
