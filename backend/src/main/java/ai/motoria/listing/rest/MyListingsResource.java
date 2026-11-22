package ai.motoria.listing.rest;

import ai.motoria.common.dto.PageResponse;
import ai.motoria.listing.dto.ListingSummaryResponse;
import ai.motoria.listing.service.ListingService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/me/listings")
@RolesAllowed({"SELLER", "DEALER", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
public class MyListingsResource {

    @Inject
    ListingService listingService;

    @GET
    public PageResponse<ListingSummaryResponse> list(@QueryParam("page") @DefaultValue("0") int page,
                                                     @QueryParam("size") @DefaultValue("20") int size) {
        return listingService.listMine(page, size);
    }
}
