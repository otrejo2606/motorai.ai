package ai.motoria.listing.rest;

import ai.motoria.common.dto.PageResponse;
import ai.motoria.listing.dto.PhotoReviewItemResponse;
import ai.motoria.listing.dto.PhotoReviewRequest;
import ai.motoria.listing.service.ListingPhotoReviewService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

/**
 * Analyst tray of the photo angle check (add-captura-guiada-fotos). The decision is audited by the service.
 */
@Path("/backoffice/photo-reviews")
@RolesAllowed({"BACKOFFICE", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BackofficePhotoReviewResource {

    @Inject
    ListingPhotoReviewService listingPhotoReviewService;

    @GET
    public PageResponse<PhotoReviewItemResponse> list(@QueryParam("page") @DefaultValue("0") int page,
                                                      @QueryParam("size") @DefaultValue("20") int size) {
        return listingPhotoReviewService.pending(page, size);
    }

    @POST
    @Path("/{listingId}/{slot}")
    public PhotoReviewItemResponse review(@PathParam("listingId") UUID listingId,
                                          @PathParam("slot") String slot,
                                          @Valid @NotNull PhotoReviewRequest request) {
        return listingPhotoReviewService.review(listingId, slot, request);
    }
}
