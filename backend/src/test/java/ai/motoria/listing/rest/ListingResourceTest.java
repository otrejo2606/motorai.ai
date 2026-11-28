package ai.motoria.listing.rest;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.common.dto.PageResponse;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.listing.domain.TrustLevel;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.dto.ListingSummaryResponse;
import ai.motoria.listing.exception.ListingNotFoundException;
import ai.motoria.listing.service.ListingPhotoService;
import ai.motoria.listing.service.ListingSearchService;
import ai.motoria.listing.service.ListingService;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.InjectMock;
import org.eclipse.microprofile.jwt.JsonWebToken;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
class ListingResourceTest {

    @InjectMock
    ListingService listingService;

    @InjectMock
    ListingSearchService listingSearchService;

    @InjectMock
    ListingPhotoService listingPhotoService;

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
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void createShouldReturnCreatedForValidSellerRequest() {
        ListingResponse response = listingResponse();
        when(listingService.create(any())).thenReturn(response);

        given()
                .contentType("application/json")
                .body(validCreateBody())
                .when()
                .post("/listings")
                .then()
                .statusCode(201)
                .header("Location", containsString("/listings/"))
                .body("id", equalTo(response.id().toString()));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void createShouldAcceptMissingTitleWhileDraft() {
        when(listingService.create(any())).thenReturn(listingResponse());

        given()
                .contentType("application/json")
                .body("""
                        {
                          "vehicleId": "%s",
                          "price": 21000,
                          "mileage": 15000,
                          "province": "SAN_JOSE",
                          "transmission": "MANUAL"
                        }
                        """.formatted(UUID.randomUUID()))
                .when()
                .post("/listings")
                .then()
                .statusCode(201);
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void createShouldReturnValidationErrorsWithLeafFields() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "price": -1,
                          "mileage": 15000,
                          "province": "SAN_JOSE",
                          "transmission": "MANUAL"
                        }
                        """)
                .when()
                .post("/listings")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("violations.field", hasItem("price"))
                .body("violations.field", hasItem("vehicleId"))
                .body("violations.field", everyItem(not(containsString("."))));
    }

    @Test
    @TestSecurity(user = "buyer-1", roles = "BUYER")
    void createShouldRejectBuyerRole() {
        given()
                .contentType("application/json")
                .body(validCreateBody())
                .when()
                .post("/listings")
                .then()
                .statusCode(403);
    }

    @Test
    void getByIdShouldBePublic() {
        ListingResponse response = listingResponse();
        when(listingService.getById(response.id())).thenReturn(response);

        given()
                .when()
                .get("/listings/%s".formatted(response.id()))
                .then()
                .statusCode(200)
                .body("id", equalTo(response.id().toString()));
    }

    @Test
    void getByIdShouldReturnNotFoundWhenServiceThrows() {
        UUID listingId = UUID.randomUUID();
        when(listingService.getById(listingId)).thenThrow(new ListingNotFoundException(listingId));

        given()
                .when()
                .get("/listings/%s".formatted(listingId))
                .then()
                .statusCode(404)
                .body("code", equalTo("LISTING_NOT_FOUND"));
    }

    @Test
    void searchShouldBePublicAndReturnAPage() {
        ListingSummaryResponse summary = new ListingSummaryResponse(UUID.randomUUID(), ListingStatus.PUBLISHED, "Toyota",
                BigDecimal.valueOf(21000), "CRC", 2021, 15000, "TOYOTA", "COROLLA", null, TrustLevel.REGISTRY_VERIFIED,
                null, UUID.randomUUID());
        when(listingSearchService.search(any())).thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1));

        given()
                .when()
                .get("/listings?brand=TOYOTA&sort=PRICE_ASC")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("page", equalTo(0))
                .body("items[0].brand", equalTo("TOYOTA"));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void markSoldShouldReturnForbiddenWhenServiceRejectsNonOwner() {
        UUID listingId = UUID.randomUUID();
        when(listingService.markSold(eq(listingId))).thenThrow(new ForbiddenOperationException("Access denied"));

        given()
                .when()
                .patch("/listings/%s/mark-sold".formatted(listingId))
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void publishShouldRejectSellerRole() {
        given()
                .when()
                .patch("/listings/%s/publish".formatted(UUID.randomUUID()))
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "buyer-1", roles = "BUYER")
    void attachPhotoShouldRejectBuyerRole() {
        given()
                .contentType("application/json")
                .body(mediaBody())
                .when()
                .put("/listings/%s/photos/FRONT".formatted(UUID.randomUUID()))
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void attachPhotoShouldReturnListingForSeller() {
        ListingResponse response = listingResponse();
        when(listingPhotoService.attach(eq(response.id()), eq("FRONT"), any())).thenReturn(response);

        given()
                .contentType("application/json")
                .body(mediaBody())
                .when()
                .put("/listings/%s/photos/FRONT".formatted(response.id()))
                .then()
                .statusCode(200)
                .body("id", equalTo(response.id().toString()));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void myListingsShouldReturnAPageForSeller() {
        when(listingService.listMine(anyInt(), anyInt())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

        given()
                .when()
                .get("/me/listings")
                .then()
                .statusCode(200)
                .body("total", equalTo(0));
    }

    @Test
    void myListingsShouldRequireAuthentication() {
        given()
                .when()
                .get("/me/listings")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void backofficeListingsShouldRejectSeller() {
        given()
                .when()
                .get("/backoffice/listings?status=PENDING_REVIEW")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void backofficeListingsShouldReturnAPageForAnalyst() {
        when(listingService.listForBackoffice(eq(ListingStatus.PENDING_REVIEW), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

        given()
                .when()
                .get("/backoffice/listings?status=PENDING_REVIEW")
                .then()
                .statusCode(200)
                .body("size", equalTo(20));
    }

    private String mediaBody() {
        return """
                {"mediaId": "%s"}
                """.formatted(UUID.randomUUID());
    }

    private String validCreateBody() {
        return """
                {
                  "vehicleId": "%s",
                  "price": 21000,
                  "mileage": 15000,
                  "province": "SAN_JOSE",
                  "transmission": "MANUAL",
                  "declaredEquipment": ["Sunroof"],
                  "maintenanceHistory": "Agency service",
                  "negotiable": true,
                  "title": "Honda Civic",
                  "description": "Single owner"
                }
                """.formatted(UUID.randomUUID());
    }

    private ListingResponse listingResponse() {
        return new ListingResponse(
                UUID.randomUUID(),
                ListingStatus.DRAFT,
                "Honda Civic",
                "Single owner",
                ListingCategory.CAR,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.valueOf(21000),
                "CRC",
                BigDecimal.valueOf(20000),
                BigDecimal.valueOf(22000),
                2022,
                15000,
                "HONDA",
                "CIVIC",
                null,
                null,
                null,
                TrustLevel.NONE,
                false,
                List.of(),
                null,
                null,
                List.of(),
                null,
                Instant.now(),
                Instant.now());
    }
}
