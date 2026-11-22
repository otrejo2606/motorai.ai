package ai.motoria.vehicle.rest;

import ai.motoria.common.dto.PageResponse;
import ai.motoria.vehicle.domain.VehicleStatus;
import ai.motoria.vehicle.dto.CorrectAttributeRequest;
import ai.motoria.vehicle.dto.RegistryReviewDetailResponse;
import ai.motoria.vehicle.dto.RegistryReviewSummaryResponse;
import ai.motoria.vehicle.dto.RejectRegistryReviewRequest;
import ai.motoria.vehicle.dto.ReviewAttributeResponse;
import ai.motoria.vehicle.service.RegistryReviewService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

@Path("/backoffice/registry-reviews")
@RolesAllowed({"BACKOFFICE", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RegistryReviewResource {

    @Inject
    RegistryReviewService registryReviewService;

    @GET
    public PageResponse<RegistryReviewSummaryResponse> list(@QueryParam("status") VehicleStatus status,
                                                            @QueryParam("page") @DefaultValue("0") int page,
                                                            @QueryParam("size") @DefaultValue("20") int size) {
        return registryReviewService.list(status, page, size);
    }

    @GET
    @Path("/{vehicleId}")
    public RegistryReviewDetailResponse detail(@PathParam("vehicleId") UUID vehicleId) {
        return registryReviewService.detail(vehicleId);
    }

    @PUT
    @Path("/{vehicleId}/attributes/{key}")
    public ReviewAttributeResponse correctAttribute(@PathParam("vehicleId") UUID vehicleId,
                                                    @PathParam("key") String key,
                                                    @Valid CorrectAttributeRequest request) {
        return registryReviewService.correctAttribute(vehicleId, key, request.value());
    }

    @POST
    @Path("/{vehicleId}/approve")
    public RegistryReviewSummaryResponse approve(@PathParam("vehicleId") UUID vehicleId) {
        return registryReviewService.approve(vehicleId);
    }

    @POST
    @Path("/{vehicleId}/reject")
    public RegistryReviewSummaryResponse reject(@PathParam("vehicleId") UUID vehicleId,
                                                @Valid RejectRegistryReviewRequest request) {
        return registryReviewService.reject(vehicleId, request.reason());
    }
}
