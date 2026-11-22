package ai.motoria.user.rest;

import ai.motoria.common.dto.PageResponse;
import ai.motoria.user.domain.IdentityVerificationStatus;
import ai.motoria.user.dto.BackofficeIdentityVerificationResponse;
import ai.motoria.user.dto.RejectIdentityVerificationRequest;
import ai.motoria.user.service.IdentityVerificationService;
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

@Path("/backoffice/identity-verifications")
@RolesAllowed({"BACKOFFICE", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BackofficeIdentityVerificationResource {

    @Inject
    IdentityVerificationService identityVerificationService;

    @GET
    public PageResponse<BackofficeIdentityVerificationResponse> list(@QueryParam("status") IdentityVerificationStatus status,
                                                                     @QueryParam("page") @DefaultValue("0") int page,
                                                                     @QueryParam("size") @DefaultValue("20") int size) {
        return identityVerificationService.listForReview(status, page, size);
    }

    @POST
    @Path("/{verificationId}/approve")
    public BackofficeIdentityVerificationResponse approve(@PathParam("verificationId") UUID verificationId) {
        return identityVerificationService.approve(verificationId);
    }

    @POST
    @Path("/{verificationId}/reject")
    public BackofficeIdentityVerificationResponse reject(@PathParam("verificationId") UUID verificationId,
                                                         @Valid RejectIdentityVerificationRequest request) {
        return identityVerificationService.reject(verificationId, request.reason());
    }
}
