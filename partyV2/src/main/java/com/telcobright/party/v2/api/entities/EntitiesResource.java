package com.telcobright.party.v2.api.entities;

import com.telcobright.party.v2.config.TenantRegistry;
import com.telcobright.party.v2.model.EntityMeta;
import com.telcobright.party.v2.providers.UserRepoProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Exposes the entity vocabulary discovered by each tenant's
 * {@link UserRepoProvider} at startup. The policy builder UI calls this to
 * populate node handles with real field names + types.
 *
 * Final URL: /api/v1/v2/tenants/{tenantId}/entities
 */
@Path("/v2/tenants")
@Produces(MediaType.APPLICATION_JSON)
public class EntitiesResource {

    @Inject
    TenantRegistry registry;

    @GET
    @Path("/{tenantId}/entities")
    public Response entities(@PathParam("tenantId") String tenantId) {
        try {
            UserRepoProvider provider = registry.provider(tenantId);
            List<EntityMeta> entities = provider.entities();
            return Response.ok(entities).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}
