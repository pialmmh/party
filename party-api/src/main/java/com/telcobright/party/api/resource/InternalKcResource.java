package com.telcobright.party.api.resource;

import com.telcobright.party.master.kc.KcUserLookupService;
import com.telcobright.party.master.kc.KcUserView;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Endpoints consumed by the Keycloak User Storage SPI jar.
 * Do NOT expose publicly. Guarded by {@link com.telcobright.party.api.security.KcIntegrationSecretFilter}.
 */
@Path("/internal/kc")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InternalKcResource {

    @Inject KcUserLookupService lookup;

    public record CredentialsRequest(String realm, String username, String password) {}
    public record CredentialsResponse(boolean valid) {}

    @GET @Path("/users/by-username")
    public Response byUsername(@QueryParam("realm") String realm,
                               @QueryParam("username") String username) {
        return lookup.byUsername(realm, username)
                .map(u -> Response.ok(u).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET @Path("/users/by-id")
    public Response byId(@QueryParam("realm") String realm,
                         @QueryParam("id") String id) {
        return lookup.byId(realm, id)
                .map(u -> Response.ok(u).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET @Path("/users/search")
    public List<KcUserView> search(@QueryParam("realm") String realm,
                                   @QueryParam("q") String q,
                                   @QueryParam("first") @DefaultValue("0") int first,
                                   @QueryParam("max") @DefaultValue("20") int max) {
        return lookup.search(realm, q, first, max);
    }

    @POST @Path("/users/validate-credentials")
    public CredentialsResponse validate(CredentialsRequest req) {
        boolean ok = lookup.validateCredentials(req.realm(), req.username(), req.password());
        return new CredentialsResponse(ok);
    }

    @GET @Path("/healthz")
    public Map<String, String> healthz() { return Map.of("status", "ok"); }
}
