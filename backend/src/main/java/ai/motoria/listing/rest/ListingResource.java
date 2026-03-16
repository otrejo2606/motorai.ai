package ai.motoria.listing.rest;

import ai.motoria.listing.dto.CreateListingRequest;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.dto.ListingSearchRequest;
import ai.motoria.listing.dto.ListingSummaryResponse;
import ai.motoria.listing.dto.UpdateListingRequest;
import ai.motoria.listing.service.ListingSearchService;
import ai.motoria.listing.service.ListingService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Path("/listings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ListingResource {

    @Inject
    ListingService listingService;

    @Inject
    ListingSearchService listingSearchService;

    @POST
    @RolesAllowed({"SELLER", "ADMIN"})
    public Response create(@Valid CreateListingRequest request) {
        ListingResponse response = listingService.create(request);
        return Response.created(URI.create("/listings/" + response.id()))
                .entity(response)
                .build();
    }

    @GET
    @RolesAllowed({"BUYER", "SELLER", "BACKOFFICE", "ADMIN"})
    public List<ListingSummaryResponse> search(@BeanParam ListingSearchRequest request) {
        return listingSearchService.search(request);
    }

    @GET
    @Path("/{listingId}")
    @RolesAllowed({"BUYER", "SELLER", "BACKOFFICE", "ADMIN"})
    public ListingResponse getById(@PathParam("listingId") UUID listingId) {
        return listingService.getById(listingId);
    }

    @PUT
    @Path("/{listingId}")
    @RolesAllowed({"SELLER", "ADMIN"})
    public ListingResponse update(@PathParam("listingId") UUID listingId, @Valid UpdateListingRequest request) {
        return listingService.update(listingId, request);
    }

    @PATCH
    @Path("/{listingId}/submit-review")
    @RolesAllowed({"SELLER", "ADMIN"})
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
    @RolesAllowed({"SELLER", "ADMIN"})
    public ListingResponse markSold(@PathParam("listingId") UUID listingId) {
        return listingService.markSold(listingId);
    }

    @PATCH
    @Path("/{listingId}/request-certification")
    @RolesAllowed({"SELLER", "ADMIN"})
    public ListingResponse requestCertification(@PathParam("listingId") UUID listingId) {
        return listingService.requestCertification(listingId);
    }
}
