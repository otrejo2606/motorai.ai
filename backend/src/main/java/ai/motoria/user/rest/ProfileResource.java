package ai.motoria.user.rest;

import ai.motoria.user.dto.UpdateProfileRequest;
import ai.motoria.user.dto.UserProfileResponse;
import ai.motoria.user.service.UserProfileService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/me/profile")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProfileResource {

    @Inject
    UserProfileService userProfileService;

    @GET
    public UserProfileResponse get() {
        return userProfileService.getMine();
    }

    @PUT
    public UserProfileResponse update(@Valid UpdateProfileRequest request) {
        return userProfileService.updateMine(request);
    }
}
