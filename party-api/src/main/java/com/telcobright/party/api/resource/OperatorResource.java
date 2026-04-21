package com.telcobright.party.api.resource;

import com.telcobright.party.master.entity.Operator;
import com.telcobright.party.master.service.OperatorService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/operators")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OperatorResource {

    @Inject OperatorService service;

    @GET public List<Operator> list() { return service.list(); }

    @GET @Path("/{id}") public Operator get(@PathParam("id") Long id) { return service.findById(id); }

    @POST public Operator create(Operator op) { return service.create(op); }

    @PATCH @Path("/{id}") public Operator update(@PathParam("id") Long id, Operator patch) {
        return service.update(id, patch);
    }

    @DELETE @Path("/{id}") public void delete(@PathParam("id") Long id) { service.delete(id); }
}
