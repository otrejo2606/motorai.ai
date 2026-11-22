package ai.motoria.media.rest;

import ai.motoria.media.dto.CreateUploadRequest;
import ai.motoria.media.dto.MediaResponse;
import ai.motoria.media.dto.PhotoSlotResponse;
import ai.motoria.media.dto.ReadUrlResponse;
import ai.motoria.media.dto.UploadTicketResponse;
import ai.motoria.media.service.MediaAccessService;
import ai.motoria.media.service.MediaUploadService;
import ai.motoria.media.service.PhotoProtocolService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
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
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/media")
@Produces(MediaType.APPLICATION_JSON)
public class MediaResource {

    @Inject
    MediaUploadService mediaUploadService;

    @Inject
    MediaAccessService mediaAccessService;

    @Inject
    PhotoProtocolService photoProtocolService;

    @POST
    @Path("/uploads")
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public Response requestUpload(@Valid CreateUploadRequest request) {
        UploadTicketResponse response = mediaUploadService.requestUpload(request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @POST
    @Path("/{mediaId}/confirm")
    @Authenticated
    public MediaResponse confirm(@PathParam("mediaId") UUID mediaId) {
        return mediaUploadService.confirm(mediaId);
    }

    @GET
    @Path("/{mediaId}/read-url")
    @Authenticated
    public ReadUrlResponse readUrl(@PathParam("mediaId") UUID mediaId) {
        return mediaAccessService.readUrl(mediaId);
    }

    @GET
    @Path("/photo-protocol")
    @PermitAll
    public List<PhotoSlotResponse> photoProtocol(@QueryParam("country") @DefaultValue("CR") String country) {
        return photoProtocolService.list(country);
    }
}
