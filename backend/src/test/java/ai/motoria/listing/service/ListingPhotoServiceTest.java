package ai.motoria.listing.service;

import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.PhotoAngleCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Attach, check without transaction and apply (design D2 of add-captura-guiada-fotos).
 */
class ListingPhotoServiceTest {

    private final ListingPhotoAngleApplier angleApplier = mock(ListingPhotoAngleApplier.class);
    private final MediaPort mediaPort = mock(MediaPort.class);
    private final ListingPhotoService service = new ListingPhotoService();
    private final UUID listingId = UUID.randomUUID();
    private final UUID mediaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service.angleApplier = angleApplier;
        service.mediaPort = mediaPort;
    }

    @Test
    void photoShouldBeAttachedThenCheckedThenApplied() {
        PhotoAngleCheckResult result = new PhotoAngleCheckResult("MATCH", "FRONT", 0.95, "v1", 12L, null, null, null);
        when(mediaPort.checkPhotoAngle(mediaId, "FRONT")).thenReturn(result);

        service.attach(listingId, "FRONT", mediaId);

        InOrder order = inOrder(angleApplier, mediaPort);
        order.verify(angleApplier).attach(listingId, "FRONT", mediaId);
        order.verify(mediaPort).checkPhotoAngle(mediaId, "FRONT");
        order.verify(angleApplier).apply(listingId, "FRONT", mediaId, result);
    }

    @Test
    void unexpectedCheckFailureShouldBeAppliedAsUnavailable() {
        when(mediaPort.checkPhotoAngle(mediaId, "VIN")).thenThrow(new IllegalStateException("boom"));

        service.attach(listingId, "VIN", mediaId);

        verify(angleApplier).apply(eq(listingId), eq("VIN"), eq(mediaId), argThat(result ->
                PhotoAngleCheckResult.UNAVAILABLE.equals(result.status())
                        && PhotoAngleCheckResult.ERROR_UNAVAILABLE.equals(result.errorCode())));
    }

    @Test
    void invalidPhotoShouldNeverReachTheClassifier() {
        doThrow(new BusinessRuleException("LISTING_PHOTO_INVALID", "Unknown photo"))
                .when(angleApplier).attach(listingId, "FRONT", mediaId);

        assertThrows(BusinessRuleException.class, () -> service.attach(listingId, "FRONT", mediaId));

        verify(mediaPort, never()).checkPhotoAngle(any(), any());
        verify(angleApplier, never()).apply(any(), any(), any(), any());
    }
}
