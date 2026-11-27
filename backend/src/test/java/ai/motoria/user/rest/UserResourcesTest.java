package ai.motoria.user.rest;

import ai.motoria.common.dto.PageResponse;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.common.exception.QuotaExceededException;
import ai.motoria.user.domain.IdentityLevel;
import ai.motoria.user.domain.IdType;
import ai.motoria.user.dto.IdentityChallengeResponse;
import ai.motoria.user.dto.PersonRegistryLookupResponse;
import ai.motoria.user.dto.UserProfileResponse;
import ai.motoria.user.service.ConsentService;
import ai.motoria.user.service.IdentityVerificationService;
import ai.motoria.user.service.PersonRegistryService;
import ai.motoria.user.service.UserProfileService;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Roles and ownership on /me/*, /backoffice/identity-verifications and /backoffice/person-registry.
 */
@QuarkusTest
class UserResourcesTest {

    @InjectMock
    UserProfileService userProfileService;

    @InjectMock
    ConsentService consentService;

    @InjectMock
    IdentityVerificationService identityVerificationService;

    @InjectMock
    PersonRegistryService personRegistryService;

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
    void profileShouldRequireAuthentication() {
        given()
                .when()
                .get("/me/profile")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "buyer-1", roles = "BUYER")
    void profileNeverReturnsTheFullIdNumber() {
        when(userProfileService.getMine()).thenReturn(new UserProfileResponse(UUID.randomUUID(), "Maria Perez",
                IdType.NATIONAL_ID, "0567", null, "maria@example.com", "CR", IdentityLevel.LEVEL_1));

        given()
                .when()
                .get("/me/profile")
                .then()
                .statusCode(200)
                .body("idNumberLast4", equalTo("0567"))
                .body("idNumber", nullValue())
                .body("idType", equalTo("NATIONAL_ID"));
    }

    @Test
    @TestSecurity(user = "buyer-1", roles = "BUYER")
    void profileUpdateAcceptsMissingIdNumber() {
        when(userProfileService.updateMine(any())).thenReturn(new UserProfileResponse(UUID.randomUUID(), "Maria Perez",
                null, null, null, null, "CR", IdentityLevel.LEVEL_1));

        given()
                .contentType("application/json")
                .body("""
                        {"fullName": "Maria Perez", "countryCode": "CR"}
                        """)
                .when()
                .put("/me/profile")
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "buyer-1", roles = "BUYER")
    void consentsReturnAPage() {
        when(consentService.listMine()).thenReturn(new PageResponse<>(List.of(), 0, 0, 0));

        given()
                .when()
                .get("/me/consents")
                .then()
                .statusCode(200)
                .body("total", equalTo(0));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void startingAVerificationReturnsTheChallenge() {
        when(identityVerificationService.start()).thenReturn(
                new IdentityChallengeResponse(UUID.randomUUID(), "ABC234", Instant.now()));

        given()
                .when()
                .post("/me/identity-verifications")
                .then()
                .statusCode(201)
                .body("challengeCode", equalTo("ABC234"));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void exhaustedQuotaReturnsStableCode() {
        when(identityVerificationService.submit(any(), any()))
                .thenThrow(new QuotaExceededException("IDENTITY_MANUAL_QUOTA_EXHAUSTED", "quota"));

        given()
                .contentType("application/json")
                .body("""
                        {"documentFrontMediaId": "%s", "documentBackMediaId": "%s", "selfieMediaId": "%s"}
                        """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .when()
                .put("/me/identity-verifications/%s".formatted(UUID.randomUUID()))
                .then()
                .statusCode(429)
                .body("code", equalTo("IDENTITY_MANUAL_QUOTA_EXHAUSTED"));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void sellerCannotApproveIdentityVerifications() {
        given()
                .when()
                .post("/backoffice/identity-verifications/%s/approve".formatted(UUID.randomUUID()))
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void analystCannotApproveTheirOwnVerification() {
        UUID verificationId = UUID.randomUUID();
        when(identityVerificationService.approve(verificationId))
                .thenThrow(new ForbiddenOperationException("IDENTITY_SELF_REVIEW_FORBIDDEN", "own"));

        given()
                .when()
                .post("/backoffice/identity-verifications/%s/approve".formatted(verificationId))
                .then()
                .statusCode(403)
                .body("code", equalTo("IDENTITY_SELF_REVIEW_FORBIDDEN"));
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void analystCanListPendingVerifications() {
        when(identityVerificationService.listForReview(any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

        given()
                .when()
                .get("/backoffice/identity-verifications?status=SUBMITTED")
                .then()
                .statusCode(200)
                .body("page", equalTo(0));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void sellerCannotLookUpPeopleInTheRegistry() {
        given()
                .contentType("application/json")
                .body("""
                        {"idType": "NATIONAL_ID", "idNumber": "102340567", "reason": "Revision de un caso de fraude"}
                        """)
                .when()
                .post("/backoffice/person-registry/lookups")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void registryLookupWithoutReasonIsRejected() {
        given()
                .contentType("application/json")
                .body("""
                        {"idType": "NATIONAL_ID", "idNumber": "102340567"}
                        """)
                .when()
                .post("/backoffice/person-registry/lookups")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void analystCanLookUpAPersonInTheRegistry() {
        when(personRegistryService.lookup(any())).thenReturn(new PersonRegistryLookupResponse(
                true, "stub", "F", "Maria Perez Rojas", LocalDate.now().plusYears(3), false, "COSTA RICA",
                Instant.now(), true));

        given()
                .contentType("application/json")
                .body("""
                        {"idType": "NATIONAL_ID", "idNumber": "102340567", "reason": "Revision de un caso de fraude"}
                        """)
                .when()
                .post("/backoffice/person-registry/lookups")
                .then()
                .statusCode(200)
                .body("found", equalTo(true))
                .body("fullName", equalTo("Maria Perez Rojas"))
                .body("idNumber", nullValue());
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void sellerCannotRecheckTheRegistryOfAVerification() {
        given()
                .when()
                .post("/backoffice/identity-verifications/%s/registry-check".formatted(UUID.randomUUID()))
                .then()
                .statusCode(403);
    }
}
