package ai.motoria.user.rest;

import ai.motoria.user.dto.PersonRegistryLookupRequest;
import ai.motoria.user.dto.PersonRegistryLookupResponse;
import ai.motoria.user.service.PersonRegistryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Person lookup by identity document in the registry (Credid) for analysts. POST so the number travels in the body
 * and never in URLs or access logs; each lookup is logged with its reason.
 */
@Path("/backoffice/person-registry")
@RolesAllowed({"BACKOFFICE", "ADMIN"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BackofficePersonRegistryResource {

    @Inject
    PersonRegistryService personRegistryService;

    @POST
    @Path("/lookups")
    public PersonRegistryLookupResponse lookup(@Valid @NotNull PersonRegistryLookupRequest request) {
        return personRegistryService.lookup(request);
    }
}
