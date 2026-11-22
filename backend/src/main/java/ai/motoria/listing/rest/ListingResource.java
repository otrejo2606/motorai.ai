package ai.motoria.listing.rest;

import ai.motoria.common.dto.PageResponse;
import ai.motoria.listing.dto.AttachPhotoRequest;
import ai.motoria.listing.dto.CopySuggestionResponse;
import ai.motoria.listing.dto.CreateListingRequest;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.dto.ListingSearchRequest;
import ai.motoria.listing.dto.ListingSummaryResponse;
import ai.motoria.listing.dto.UpdateListingRequest;
import ai.motoria.listing.service.ListingCopyAssistService;
import ai.motoria.listing.service.ListingPhotoService;
import ai.motoria.listing.service.ListingSearchService;
import ai.motoria.listing.service.ListingService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.UUID;

@Path("/listings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ListingResource {

    @Inject
    ListingService listingService;

    @Inject
    ListingSearchService listingSearchService;

    @Inject
    ListingPhotoService listingPhotoService;

    @Inject
    ListingCopyAssistService listingCopyAssistService;

    @POST
    @RolesAllowed({"SELLER", "DEALER", "ADMIN"})
    public Response create(@Valid CreateListingRequest request) {
        ListingResponse response = listingService.create(request);
        return Response.created(URI.create("/listings/" + response.id()))
                .entity(response)
                .build();
    }

    @GET
    @PermitAll
    public PageResponse<ListingSummaryResponse> search(@BeanParam ListingSearchRequest request) {
        return listingSearchService.search(request);
    }

    @GET
    @Path("/{listingId}")
    @PermitAll
    public ListingResponse getById(@PathParam("listingId") UUID listingId) {
        return listingService.getById(listingId);
    }

    @PUT
    @Path("/{listingId}")
    @RolesAllowed({"SELLER", "DEALER", "ADMIN"})
    public ListingResponse update(@PathParam("listingId") UUID listingId, @Valid UpdateListingRequest request) {
        return listingService.update(listingId, request);
    }

    @PUT
    @Path("/{listingId}/photos/{slot}")
    @RolesAllowed({"SELLER", "DEALER", "ADMIN"})
    public ListingResponse attachPhoto(@PathParam("listingId") UUID listingId,
                                       @PathParam("slot") String slot,
                                       @Valid AttachPhotoRequest request) {
        return listingPhotoService.attach(listingId, slot, request.mediaId());
    }

    @DELETE
    @Path("/{listingId}/photos/{slot}")
    @RolesAllowed({"SELLER", "DEALER", "ADMIN"})
    public ListingResponse removePhoto(@PathParam("listingId") UUID listingId, @PathParam("slot") String slot) {
        return listingPhotoService.remove(listingId, slot);
    }

    @POST
    @Path("/{listingId}/copy-suggestions")
    @RolesAllowed({"SELLER", "DEALER", "ADMIN"})
    public CopySuggestionResponse copySuggestions(@PathParam("listingId") UUID listingId,
                                                  @QueryParam("language") @DefaultValue("es") String language) {
        return listingCopyAssistService.suggest(listingId, language);
    }

    @PATCH
    @Path("/{listingId}/submit-review")
    @RolesAllowed({"SELLER", "DEALER", "ADMIN"})
    public ListingResponse submitForReview(@PathParam("listingId") UUID listingId) {
        return listingService.submitForReview(listingId);
    }

    @PATCH
    @Path("/{listingId}/publish")
    @RolesAllowed({"BACKOFFICE", "ADMIN"})
    public ListingResponse publish(@PathParam("listingId") UUID listingId) {
        return listingService.publish(listingId);
    }

    @PATCH
    @Path("/{listingId}/mark-sold")
    @RolesAllowed({"SELLER", "DEALER", "ADMIN"})
    public ListingResponse markSold(@PathParam("listingId") UUID listingId) {
        return listingService.markSold(listingId);
    }

    @PATCH
    @Path("/{listingId}/request-certification")
    @RolesAllowed({"SELLER", "DEALER", "ADMIN"})
    public ListingResponse requestCertification(@PathParam("listingId") UUID listingId) {
        return listingService.requestCertification(listingId);
    }
}
