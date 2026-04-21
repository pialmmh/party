package com.telcobright.party.api.resource;

import com.telcobright.party.master.entity.Tenant;
import com.telcobright.party.master.service.TenantService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/tenants")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TenantResource {

    @Inject TenantService service;

    @GET @Path("/{id}")
    public Tenant get(@PathParam("id") Long id) { return service.findById(id); }

    @PATCH @Path("/{id}")
    public Tenant update(@PathParam("id") Long id, Tenant patch) { return service.update(id, patch); }

    @DELETE @Path("/{id}")
    public void delete(@PathParam("id") Long id) { service.delete(id); }
}
