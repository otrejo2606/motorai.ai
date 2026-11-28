package ai.motoria.user.rest;

import ai.motoria.user.dto.IdentityChallengeResponse;
import ai.motoria.user.dto.IdentityVerificationResponse;
import ai.motoria.user.dto.SubmitIdentityVerificationRequest;
import ai.motoria.user.service.IdentityVerificationService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/me/identity-verifications")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IdentityVerificationResource {

    @Inject
    IdentityVerificationService identityVerificationService;

    @POST
    public Response start() {
        IdentityChallengeResponse response = identityVerificationService.start();
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @PUT
    @Path("/{verificationId}")
    public IdentityVerificationResponse submit(@PathParam("verificationId") UUID verificationId,
                                               @Valid SubmitIdentityVerificationRequest request) {
        return identityVerificationService.submit(verificationId, request);
    }

    @GET
    @Path("/latest")
    public IdentityVerificationResponse latest() {
        return identityVerificationService.latestMine();
    }
}
