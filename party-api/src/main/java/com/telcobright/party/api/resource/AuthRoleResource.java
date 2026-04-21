package com.telcobright.party.api.resource;

import com.telcobright.party.master.entity.AuthRole;
import com.telcobright.party.master.service.AuthRoleService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/tenants/{tenantId}/roles")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthRoleResource {

    @Inject AuthRoleService service;

    public record PermissionsRequest(List<Long> permissionIds) {}

    @GET public List<AuthRole> list(@PathParam("tenantId") Long tenantId) { return service.listByTenant(tenantId); }

    @GET @Path("/{id}")
    public AuthRole get(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id) {
        return service.findById(tenantId, id);
    }

    @POST public AuthRole create(@PathParam("tenantId") Long tenantId, AuthRole r) {
        return service.create(tenantId, r);
    }

    @PATCH @Path("/{id}")
    public AuthRole update(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id, AuthRole patch) {
        return service.update(tenantId, id, patch);
    }

    @DELETE @Path("/{id}")
    public void delete(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id) {
        service.delete(tenantId, id);
    }

    @POST @Path("/{id}/permissions")
    public void setPermissions(@PathParam("tenantId") Long tenantId,
                               @PathParam("id") Long id,
                               PermissionsRequest r) {
        service.replacePermissions(tenantId, id, r.permissionIds());
    }
}
