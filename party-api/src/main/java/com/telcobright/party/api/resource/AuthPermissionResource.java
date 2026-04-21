package com.telcobright.party.api.resource;

import com.telcobright.party.master.entity.AuthPermission;
import com.telcobright.party.master.service.AuthPermissionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/tenants/{tenantId}/permissions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthPermissionResource {

    @Inject AuthPermissionService service;

    @GET public List<AuthPermission> list(@PathParam("tenantId") Long tenantId) { return service.listByTenant(tenantId); }

    @POST public AuthPermission create(@PathParam("tenantId") Long tenantId, AuthPermission p) {
        return service.create(tenantId, p);
    }

    @DELETE @Path("/{id}")
    public void delete(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id) {
        service.delete(tenantId, id);
    }
}
