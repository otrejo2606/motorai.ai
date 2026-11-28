package ai.motoria.listing.service;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.listing.domain.ListingPublicationPolicy;
import ai.motoria.listing.domain.PriceRange;
import ai.motoria.listing.domain.Province;
import ai.motoria.listing.domain.Transmission;
import ai.motoria.listing.domain.TrustLevel;
import ai.motoria.listing.dto.CreateListingRequest;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.dto.UpdateListingRequest;
import ai.motoria.listing.event.ListingEventProducer;
import ai.motoria.listing.exception.ListingNotFoundException;
import ai.motoria.listing.integration.AiPriceIntegration;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    ListingRepository listingRepository;

    @Mock
    ListingMapper listingMapper;

    @Mock
    AiPriceIntegration aiPriceIntegration;

    @Mock
    ListingEventProducer listingEventProducer;

    @Mock
    ListingPersistenceService listingPersistenceService;

    @Mock
    ListingAccess listingAccess;

    @Mock
    ListingPublicationGuard listingPublicationGuard;

    @Mock
    VehicleRegistryPort vehicleRegistryPort;

    @Mock
    CurrentUser currentUser;

    @InjectMocks
    ListingService listingService;

    @Test
    void createShouldSanitizeInputAndDelegateToPersistenceService() {
        UUID sellerId = UUID.randomUUID();
        CreateListingRequest request = new CreateListingRequest(
                UUID.randomUUID(),
                BigDecimal.valueOf(15000),
                null,
                25000,
                Province.SAN_JOSE,
                Transmission.MANUAL,
                List.of("<b>Sunroof</b>", " ", "<script>x</script>"),
                "<i>Agency</i>",
                true,
                "<b>Fast Car</b>",
                "<script>alert('x')</script>Clean description");
        PriceRange priceRange = new PriceRange(BigDecimal.valueOf(14000), BigDecimal.valueOf(16000));
        ListingResponse expected = listingResponse(sellerId);

        when(currentUser.id()).thenReturn(sellerId);
        when(aiPriceIntegration.recommendPriceRange(request.price())).thenReturn(priceRange);
        when(listingPersistenceService.persistListing(any(CreateListingRequest.class), eq(sellerId), eq(priceRange))).thenReturn(expected);

        ListingResponse actual = listingService.create(request);

        ArgumentCaptor<CreateListingRequest> captor = ArgumentCaptor.forClass(CreateListingRequest.class);
        verify(listingPersistenceService).persistListing(captor.capture(), eq(sellerId), eq(priceRange));
        assertEquals("Fast Car", captor.getValue().title());
        assertEquals("Clean description", captor.getValue().description());
        assertEquals(List.of("Sunroof"), captor.getValue().declaredEquipment());
        assertEquals("Agency", captor.getValue().maintenanceHistory());
        assertSame(expected, actual);
    }

    @Test
    void updateShouldSanitizeInputAndDelegateToPersistenceService() {
        UUID listingId = UUID.randomUUID();
        UpdateListingRequest request = new UpdateListingRequest(
                BigDecimal.valueOf(18000),
                "USD",
                18000,
                Province.CARTAGO,
                Transmission.AUTOMATIC,
                List.of(),
                null,
                false,
                "<i>Updated title</i>",
                "<div>Updated description</div>");
        PriceRange priceRange = new PriceRange(BigDecimal.valueOf(17000), BigDecimal.valueOf(19000));
        ListingResponse expected = listingResponse(UUID.randomUUID());

        when(aiPriceIntegration.recommendPriceRange(request.price())).thenReturn(priceRange);
        when(listingPersistenceService.applyUpdate(eq(listingId), any(UpdateListingRequest.class), eq(priceRange))).thenReturn(expected);

        ListingResponse actual = listingService.update(listingId, request);

        ArgumentCaptor<UpdateListingRequest> captor = ArgumentCaptor.forClass(UpdateListingRequest.class);
        verify(listingPersistenceService).applyUpdate(eq(listingId), captor.capture(), eq(priceRange));
        assertEquals("Updated title", captor.getValue().title());
        assertEquals("Updated description", captor.getValue().description());
        assertSame(expected, actual);
    }

    @Test
    void getByIdShouldThrowWhenListingDoesNotExist() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findByIdOptional(listingId)).thenReturn(Optional.empty());

        assertThrows(ListingNotFoundException.class, () -> listingService.getById(listingId));
    }

    @Test
    void getByIdShouldHideDraftFromPublicViewers() {
        Listing listing = ownedBy(UUID.randomUUID());
        when(listingRepository.findByIdOptional(listing.getId())).thenReturn(Optional.of(listing));
        when(listingAccess.isPrivilegedViewer(listing)).thenReturn(false);

        assertThrows(ListingNotFoundException.class, () -> listingService.getById(listing.getId()));
    }

    @Test
    void getByIdShouldShowDraftToOwner() {
        Listing listing = ownedBy(UUID.randomUUID());
        ListingResponse response = listingResponse(listing.getSellerId());
        when(listingRepository.findByIdOptional(listing.getId())).thenReturn(Optional.of(listing));
        when(listingAccess.isPrivilegedViewer(listing)).thenReturn(true);
        when(vehicleRegistryPort.findVehicle(listing.getVehicleId())).thenReturn(Optional.empty());
        when(listingMapper.toResponse(listing, null, true)).thenReturn(response);

        assertSame(response, listingService.getById(listing.getId()));
    }

    @Test
    void submitForReviewShouldNotChangeStateWhenPublicationRulesFail() {
        Listing listing = ownedBy(UUID.randomUUID());
        when(listingAccess.find(listing.getId())).thenReturn(listing);
        doThrow(new BusinessRuleException(ListingPublicationPolicy.PHOTOS_INCOMPLETE, "rules"))
                .when(listingPublicationGuard).assertPublishable(listing);

        BusinessRuleException exception = assertThrows(BusinessRuleException.class,
                () -> listingService.submitForReview(listing.getId()));

        assertEquals(ListingPublicationPolicy.PHOTOS_INCOMPLETE, exception.getCode());
        assertEquals(ListingStatus.DRAFT, listing.getStatus());
        verify(listingEventProducer, never()).listingUpdated(any());
    }

    @Test
    void requestCertificationShouldRejectNonOwner() {
        Listing listing = ownedBy(UUID.randomUUID());
        when(listingAccess.find(listing.getId())).thenReturn(listing);
        doThrow(new ForbiddenOperationException("denied")).when(listingAccess).assertOwner(listing);

        assertThrows(ForbiddenOperationException.class, () -> listingService.requestCertification(listing.getId()));
        verify(listingEventProducer, never()).certificationRequested(any());
    }

    @Test
    void requestCertificationShouldPublishEventForOwner() {
        UUID ownerId = UUID.randomUUID();
        Listing listing = ownedBy(ownerId);
        ListingResponse response = listingResponse(ownerId);

        when(listingAccess.find(listing.getId())).thenReturn(listing);
        when(vehicleRegistryPort.findVehicle(listing.getVehicleId())).thenReturn(Optional.empty());
        when(listingMapper.toResponse(listing, null, true)).thenReturn(response);

        ListingResponse actual = listingService.requestCertification(listing.getId());

        verify(listingEventProducer).certificationRequested(listing);
        assertSame(response, actual);
    }

    private Listing ownedBy(UUID ownerId) {
        return Listing.create(
                ownerId,
                UUID.randomUUID(),
                "CR",
                ListingCategory.CAR,
                "Title",
                "Description",
                BigDecimal.valueOf(10000),
                "CRC",
                15000);
    }

    private ListingResponse listingResponse(UUID sellerId) {
        return new ListingResponse(
                UUID.randomUUID(),
                ListingStatus.DRAFT,
                "Title",
                "Description",
                ListingCategory.CAR,
                sellerId,
                UUID.randomUUID(),
                BigDecimal.valueOf(10000),
                "CRC",
                BigDecimal.valueOf(9000),
                BigDecimal.valueOf(11000),
                2021,
                10000,
                "TOYOTA",
                "COROLLA",
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
