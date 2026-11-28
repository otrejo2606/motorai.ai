package ai.motoria.user.rest;

import ai.motoria.common.dto.PageResponse;
import ai.motoria.common.security.ClientAddress;
import ai.motoria.user.dto.ConsentRequest;
import ai.motoria.user.dto.ConsentResponse;
import ai.motoria.user.service.ConsentService;
import io.quarkus.security.Authenticated;
import io.vertx.core.http.HttpServerRequest;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;


@Path("/me/consents")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConsentResource {

    @Inject
    ConsentService consentService;

    @GET
    public PageResponse<ConsentResponse> list() {
        return consentService.listMine();
    }

    @POST
    public Response record(@Valid ConsentRequest request, @Context HttpServerRequest httpRequest) {
        ConsentResponse response = consentService.record(request, ClientAddress.resolve(httpRequest));
        return Response.status(Response.Status.CREATED).entity(response).build();
    }
}
