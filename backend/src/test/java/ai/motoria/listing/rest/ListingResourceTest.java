package ai.motoria.listing.rest;

import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.common.domain.ListingStatus;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.exception.ListingNotFoundException;
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
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
                .body("""
                        {
                          "title": "Honda Civic",
                          "description": "Single owner",
                          "category": "CAR",
                          "vehicleSpecId": "%s",
                          "price": 21000,
                          "modelYear": 2022,
                          "mileage": 15000
                        }
                        """.formatted(UUID.randomUUID()))
                .when()
                .post("/listings")
                .then()
                .statusCode(201)
                .header("Location", containsString("/listings/"))
                .body("id", equalTo(response.id().toString()));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void createShouldReturnValidationErrorsWithLeafFields() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "title": "",
                          "description": "ok",
                          "category": "CAR",
                          "price": 21000,
                          "modelYear": 2022,
                          "mileage": 15000
                        }
                        """)
                .when()
                .post("/listings")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                                .body("violations.field", hasItem("title"))
                .body("violations.field", hasItem("vehicleSpecId"))
                .body("violations.field", everyItem(not(containsString("."))));
    }

    @Test
    @TestSecurity(user = "buyer-1", roles = "BUYER")
    void createShouldRejectBuyerRole() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "title": "Honda Civic",
                          "description": "Single owner",
                          "category": "CAR",
                          "vehicleSpecId": "%s",
                          "price": 21000,
                          "modelYear": 2022,
                          "mileage": 15000
                        }
                        """.formatted(UUID.randomUUID()))
                .when()
                .post("/listings")
                .then()
                .statusCode(403);
    }

    @Test
    void getByIdShouldRequireAuthentication() {
        given()
                .when()
                .get("/listings/%s".formatted(UUID.randomUUID()))
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "buyer-1", roles = "BUYER")
    void getByIdShouldReturnNotFoundWhenServiceThrows() {
        UUID listingId = UUID.randomUUID();
        when(listingService.getById(listingId)).thenThrow(new ListingNotFoundException(listingId));

        given()
                .when()
                .get("/listings/%s".formatted(listingId))
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
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

    private ListingResponse listingResponse() {
        return new ListingResponse(
                UUID.randomUUID(),
                "Honda Civic",
                "Single owner",
                ListingStatus.DRAFT,
                ListingCategory.CAR,
                UUID.randomUUID(),
                UUID.randomUUID(),
                BigDecimal.valueOf(21000),
                BigDecimal.valueOf(20000),
                BigDecimal.valueOf(22000),
                2022,
                15000,
                Instant.now(),
                Instant.now());
    }
}