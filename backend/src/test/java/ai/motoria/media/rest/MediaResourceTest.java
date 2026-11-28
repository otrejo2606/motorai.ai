package ai.motoria.media.rest;

import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.media.dto.PhotoSlotResponse;
import ai.motoria.media.dto.UploadTicketResponse;
import ai.motoria.media.service.MediaAccessService;
import ai.motoria.media.service.MediaUploadService;
import ai.motoria.media.service.PhotoProtocolService;
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
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
class MediaResourceTest {

    @InjectMock
    MediaUploadService mediaUploadService;

    @InjectMock
    MediaAccessService mediaAccessService;

    @InjectMock
    PhotoProtocolService photoProtocolService;

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
    void photoProtocolShouldBePublic() {
        when(photoProtocolService.list("CR")).thenReturn(List.of(
                new PhotoSlotResponse("FRONT", true, 1, "photoProtocol.slots.FRONT.instruction")));

        given()
                .when()
                .get("/media/photo-protocol?country=CR")
                .then()
                .statusCode(200)
                .body("[0].code", equalTo("FRONT"))
                .body("[0].instructionKey", equalTo("photoProtocol.slots.FRONT.instruction"));
    }

    @Test
    void uploadsShouldRequireAuthentication() {
        given()
                .contentType("application/json")
                .body("""
                        {"kind": "REGISTRY_DOCUMENT", "contentType": "application/pdf", "sizeBytes": 1000}
                        """)
                .when()
                .post("/media/uploads")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void sellerGetsAPresignedUpload() {
        UUID mediaId = UUID.randomUUID();
        when(mediaUploadService.requestUpload(any())).thenReturn(
                new UploadTicketResponse(mediaId, "https://s3.example/upload", Instant.now()));

        given()
                .contentType("application/json")
                .body("""
                        {"kind": "REGISTRY_DOCUMENT", "contentType": "application/pdf", "sizeBytes": 1000}
                        """)
                .when()
                .post("/media/uploads")
                .then()
                .statusCode(201)
                .body("mediaId", equalTo(mediaId.toString()));
    }

    @Test
    @TestSecurity(user = "seller-1", roles = "SELLER")
    void uploadWithoutSizeShouldFailValidation() {
        given()
                .contentType("application/json")
                .body("""
                        {"kind": "REGISTRY_DOCUMENT", "contentType": "application/pdf"}
                        """)
                .when()
                .post("/media/uploads")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    @TestSecurity(user = "seller-2", roles = "SELLER")
    void readUrlOfAnotherUsersFileShouldBeForbidden() {
        UUID mediaId = UUID.randomUUID();
        when(mediaAccessService.readUrl(mediaId)).thenThrow(new ForbiddenOperationException("MEDIA_ACCESS_DENIED", "denied"));

        given()
                .when()
                .get("/media/%s/read-url".formatted(mediaId))
                .then()
                .statusCode(403)
                .body("code", equalTo("MEDIA_ACCESS_DENIED"));
    }

    @Test
    @TestSecurity(user = "analyst-1", roles = "BACKOFFICE")
    void onlyAdminCanEditThePhotoProtocol() {
        given()
                .contentType("application/json")
                .body("""
                        {"required": true, "order": 1, "instructionKey": "photoProtocol.slots.FRONT.instruction"}
                        """)
                .when()
                .put("/backoffice/photo-protocol/CR/FRONT")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin-1", roles = "ADMIN")
    void adminCanEditThePhotoProtocol() {
        when(photoProtocolService.upsert(any(), any(), any())).thenReturn(
                new PhotoSlotResponse("FRONT", false, 3, "photoProtocol.slots.FRONT.instruction"));

        given()
                .contentType("application/json")
                .body("""
                        {"required": false, "order": 3, "instructionKey": "photoProtocol.slots.FRONT.instruction"}
                        """)
                .when()
                .put("/backoffice/photo-protocol/CR/FRONT")
                .then()
                .statusCode(200)
                .body("order", equalTo(3));
    }
}
