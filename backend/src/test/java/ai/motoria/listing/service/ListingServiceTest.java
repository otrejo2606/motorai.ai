package ai.motoria.listing.service;

import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.common.domain.ListingStatus;
import ai.motoria.listing.domain.PriceRange;
import ai.motoria.listing.dto.CreateListingRequest;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.dto.UpdateListingRequest;
import ai.motoria.listing.event.ListingEventProducer;
import ai.motoria.listing.exception.ListingNotFoundException;
import ai.motoria.listing.integration.AiPriceIntegration;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingRepository;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    JsonWebToken jwt;

    @InjectMocks
    ListingService listingService;

    @Test
    void createShouldSanitizeInputAndDelegateToPersistenceService() {
        UUID sellerId = UUID.randomUUID();
        CreateListingRequest request = new CreateListingRequest(
                "<b>Fast Car</b>",
                "<script>alert('x')</script>Clean description",
                ListingCategory.CAR,
                UUID.randomUUID(),
                BigDecimal.valueOf(15000),
                2020,
                25000);
        PriceRange priceRange = new PriceRange(BigDecimal.valueOf(14000), BigDecimal.valueOf(16000));
        ListingResponse expected = listingResponse(sellerId);

        when(jwt.getSubject()).thenReturn(sellerId.toString());
        when(aiPriceIntegration.recommendPriceRange(request.price())).thenReturn(priceRange);
        when(listingPersistenceService.persistListing(any(CreateListingRequest.class), eq(sellerId), eq(priceRange))).thenReturn(expected);

        ListingResponse actual = listingService.create(request);

        ArgumentCaptor<CreateListingRequest> captor = ArgumentCaptor.forClass(CreateListingRequest.class);
        verify(listingPersistenceService).persistListing(captor.capture(), eq(sellerId), eq(priceRange));
        assertEquals("Fast Car", captor.getValue().title());
        assertEquals("Clean description", captor.getValue().description());
        verify(aiPriceIntegration).recommendPriceRange(request.price());
        assertSame(expected, actual);
    }

    @Test
    void updateShouldSanitizeInputAndDelegateToPersistenceService() {
        UUID listingId = UUID.randomUUID();
        UpdateListingRequest request = new UpdateListingRequest(
                "<i>Updated title</i>",
                "<div>Updated description</div>",
                BigDecimal.valueOf(18000),
                2021,
                18000);
        PriceRange priceRange = new PriceRange(BigDecimal.valueOf(17000), BigDecimal.valueOf(19000));
        ListingResponse expected = listingResponse(UUID.randomUUID());

        when(aiPriceIntegration.recommendPriceRange(request.price())).thenReturn(priceRange);
        when(listingPersistenceService.applyUpdate(eq(listingId), any(UpdateListingRequest.class), eq(priceRange))).thenReturn(expected);

        ListingResponse actual = listingService.update(listingId, request);

        ArgumentCaptor<UpdateListingRequest> captor = ArgumentCaptor.forClass(UpdateListingRequest.class);
        verify(listingPersistenceService).applyUpdate(eq(listingId), captor.capture(), eq(priceRange));
        assertEquals("Updated title", captor.getValue().title());
        assertEquals("Updated description", captor.getValue().description());
        verify(aiPriceIntegration).recommendPriceRange(request.price());
        assertSame(expected, actual);
    }

    @Test
    void getByIdShouldThrowWhenListingDoesNotExist() {
        UUID listingId = UUID.randomUUID();
        when(listingRepository.findByIdOptional(listingId)).thenReturn(Optional.empty());

        assertThrows(ListingNotFoundException.class, () -> listingService.getById(listingId));
    }

    @Test
    void requestCertificationShouldRejectNonOwner() {
        UUID listingId = UUID.randomUUID();
        Listing listing = ownedBy(UUID.randomUUID());
        when(jwt.getSubject()).thenReturn(UUID.randomUUID().toString());
        when(listingRepository.findByIdOptional(listingId)).thenReturn(Optional.of(listing));

        assertThrows(ForbiddenOperationException.class, () -> listingService.requestCertification(listingId));
        verify(listingEventProducer, never()).certificationRequested(any());
    }

    @Test
    void requestCertificationShouldPublishEventForOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        Listing listing = ownedBy(ownerId);
        ListingResponse response = listingResponse(ownerId);

        when(jwt.getSubject()).thenReturn(ownerId.toString());
        when(listingRepository.findByIdOptional(listingId)).thenReturn(Optional.of(listing));
        when(listingMapper.toResponse(listing)).thenReturn(response);

        ListingResponse actual = listingService.requestCertification(listingId);

        verify(listingEventProducer).certificationRequested(listing);
        assertSame(response, actual);
    }

    private Listing ownedBy(UUID ownerId) {
        return Listing.create(
                ownerId,
                UUID.randomUUID(),
                ListingCategory.CAR,
                "Title",
                "Description",
                BigDecimal.valueOf(10000),
                2021,
                15000);
    }

    private ListingResponse listingResponse(UUID sellerId) {
        return new ListingResponse(
                UUID.randomUUID(),
                "Title",
                "Description",
                ListingStatus.DRAFT,
                ListingCategory.CAR,
                sellerId,
                UUID.randomUUID(),
                BigDecimal.valueOf(10000),
                BigDecimal.valueOf(9000),
                BigDecimal.valueOf(11000),
                2021,
                10000,
                Instant.now(),
                Instant.now());
    }
}