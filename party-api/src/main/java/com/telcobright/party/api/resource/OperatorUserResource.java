package com.telcobright.party.api.resource;

import com.telcobright.party.domain.OperatorRole;
import com.telcobright.party.domain.UserStatus;
import com.telcobright.party.master.entity.OperatorUser;
import com.telcobright.party.master.service.OperatorUserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/operator-users")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OperatorUserResource {

    @Inject OperatorUserService service;

    public record CreateRequest(String email, String password, String firstName, String lastName,
                                OperatorRole role, Long operatorId) {}
    public record PasswordRequest(String password) {}
    public record StatusRequest(UserStatus status) {}

    @GET public List<OperatorUser> list() { return service.list(); }
    @GET @Path("/{id}") public OperatorUser get(@PathParam("id") Long id) { return service.findById(id); }

    @POST public OperatorUser create(CreateRequest r) {
        return service.create(r.email(), r.password(), r.firstName(), r.lastName(), r.role(), r.operatorId());
    }

    @POST @Path("/{id}/password")
    public void resetPassword(@PathParam("id") Long id, PasswordRequest r) {
        service.resetPassword(id, r.password());
    }

    @POST @Path("/{id}/status")
    public void setStatus(@PathParam("id") Long id, StatusRequest r) {
        service.setStatus(id, r.status());
    }

    @DELETE @Path("/{id}") public void delete(@PathParam("id") Long id) { service.delete(id); }
}
