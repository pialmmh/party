package com.telcobright.party.api.resource;

import com.telcobright.party.master.entity.Tenant;
import com.telcobright.party.master.service.TenantService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/tenants")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TenantResource {

    @Inject TenantService service;

    @GET
    public List<Tenant> listForBoundOperator() {
        return service.listForBoundOperator();
    }

    @POST
    public Tenant createForBoundOperator(Tenant t) {
        return service.createForBoundOperator(t);
    }

    @GET @Path("/{id}")
    public Tenant get(@PathParam("id") Long id) { return service.findById(id); }

    @PATCH @Path("/{id}")
    public Tenant update(@PathParam("id") Long id, Tenant patch) { return service.update(id, patch); }

    @DELETE @Path("/{id}")
    public void delete(@PathParam("id") Long id) { service.delete(id); }
}
