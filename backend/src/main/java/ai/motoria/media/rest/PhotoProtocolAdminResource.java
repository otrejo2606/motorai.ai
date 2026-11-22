package ai.motoria.media.rest;

import ai.motoria.media.dto.PhotoSlotResponse;
import ai.motoria.media.dto.UpdatePhotoSlotRequest;
import ai.motoria.media.service.PhotoProtocolService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/backoffice/photo-protocol")
@RolesAllowed("ADMIN")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PhotoProtocolAdminResource {

    @Inject
    PhotoProtocolService photoProtocolService;

    @PUT
    @Path("/{country}/{code}")
    public PhotoSlotResponse upsert(@PathParam("country") String country,
                                    @PathParam("code") String code,
                                    @Valid UpdatePhotoSlotRequest request) {
        return photoProtocolService.upsert(country, code, request);
    }
}
