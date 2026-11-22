package ai.motoria.vehicle.rest;

import ai.motoria.vehicle.dto.AttachRegistryDocumentRequest;
import ai.motoria.vehicle.dto.CreateRegistryRequestRequest;
import ai.motoria.vehicle.dto.RegisterVehicleRequest;
import ai.motoria.vehicle.dto.RegistryRequestResponse;
import ai.motoria.vehicle.dto.VehicleResponse;
import ai.motoria.vehicle.service.RegistryRequestService;
import ai.motoria.vehicle.service.VehicleService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/vehicles")
@RolesAllowed({"SELLER", "DEALER", "BACKOFFICE", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VehicleResource {

    @Inject
    VehicleService vehicleService;

    @Inject
    RegistryRequestService registryRequestService;

    @POST
    public VehicleResponse register(@Valid RegisterVehicleRequest request) {
        return vehicleService.register(request);
    }

    @GET
    @Path("/{vehicleId}")
    public VehicleResponse get(@PathParam("vehicleId") UUID vehicleId) {
        return vehicleService.get(vehicleId);
    }

    @POST
    @Path("/{vehicleId}/registry-documents")
    @RolesAllowed({"SELLER", "DEALER", "ADMIN"})
    public VehicleResponse attachRegistryDocument(@PathParam("vehicleId") UUID vehicleId,
                                                  @Valid AttachRegistryDocumentRequest request) {
        return vehicleService.attachRegistryDocument(vehicleId, request.mediaId());
    }

    @POST
    @Path("/{vehicleId}/registry-requests")
    @RolesAllowed({"SELLER", "DEALER", "ADMIN"})
    public Response requestRegistry(@PathParam("vehicleId") UUID vehicleId,
                                    @Valid CreateRegistryRequestRequest request) {
        RegistryRequestResponse response = registryRequestService.create(vehicleId, request.acceptedFee());
        return Response.status(Response.Status.CREATED).entity(response).build();
    }
}
