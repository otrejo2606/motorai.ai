package ai.motoria.vehicle.rest;

import ai.motoria.common.dto.PageResponse;
import ai.motoria.vehicle.domain.RegistryRequestStatus;
import ai.motoria.vehicle.dto.FulfillRegistryRequestRequest;
import ai.motoria.vehicle.dto.RegistryRequestResponse;
import ai.motoria.vehicle.service.RegistryRequestService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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

@Path("/backoffice/registry-requests")
@RolesAllowed({"BACKOFFICE", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RegistryRequestBackofficeResource {

    @Inject
    RegistryRequestService registryRequestService;

    @GET
    public PageResponse<RegistryRequestResponse> list(@QueryParam("status") RegistryRequestStatus status,
                                                      @QueryParam("page") @DefaultValue("0") int page,
                                                      @QueryParam("size") @DefaultValue("20") int size) {
        return registryRequestService.list(status, page, size);
    }

    @POST
    @Path("/{requestId}/document")
    public RegistryRequestResponse fulfill(@PathParam("requestId") UUID requestId,
                                           @Valid FulfillRegistryRequestRequest request) {
        return registryRequestService.fulfill(requestId, request.mediaId());
    }
}
