package com.telcobright.party.api.resource;

import com.telcobright.party.master.entity.Tenant;
import com.telcobright.party.master.service.TenantService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/operators/{operatorId}/tenants")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OperatorTenantResource {

    @Inject TenantService service;

    @GET
    public List<Tenant> listByOperator(@PathParam("operatorId") Long operatorId) {
        return service.listByOperator(operatorId);
    }

    @POST
    public Tenant create(@PathParam("operatorId") Long operatorId, Tenant t) {
        return service.create(operatorId, t);
    }
}
