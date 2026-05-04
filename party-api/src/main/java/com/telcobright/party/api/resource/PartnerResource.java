package com.telcobright.party.api.resource;

import com.telcobright.party.master.entity.AuthUser;
import com.telcobright.party.master.entity.Partner;
import com.telcobright.party.master.entity.PartnerExtra;
import com.telcobright.party.master.service.AuthUserService;
import com.telcobright.party.master.service.PartnerExtraService;
import com.telcobright.party.master.service.PartnerService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@Path("/tenants/{tenantId}/partners")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PartnerResource {

    public record CreateUserRequest(String email, String password, String firstName, String lastName,
                                    String phone) {}

    @Inject PartnerService service;
    @Inject PartnerExtraService extraService;
    @Inject AuthUserService users;

    @GET public List<Partner> list(@PathParam("tenantId") Long tenantId) {
        return service.listByTenant(tenantId);
    }

    @GET @Path("/{id}")
    public Partner get(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id) {
        return service.findById(tenantId, id);
    }

    @POST
    public Partner create(@PathParam("tenantId") Long tenantId, Partner p) {
        return service.create(tenantId, p);
    }

    @PATCH @Path("/{id}")
    public Partner update(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id, Partner patch) {
        return service.update(tenantId, id, patch);
    }

    @DELETE @Path("/{id}")
    public void delete(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id) {
        service.delete(tenantId, id);
    }

    @GET @Path("/{id}/extra")
    public PartnerExtra getExtra(@PathParam("tenantId") Long tenantId, @PathParam("id") Long partnerId) {
        PartnerExtra e = extraService.findByPartner(tenantId, partnerId);
        if (e == null) throw new NotFoundException();
        return e;
    }

    @PUT @Path("/{id}/extra")
    public PartnerExtra upsertExtra(@PathParam("tenantId") Long tenantId,
                                    @PathParam("id") Long partnerId,
                                    PartnerExtra extra) {
        return extraService.upsert(tenantId, partnerId, extra);
    }

    // Partner-scoped users — kept here (not in AuthUserResource) to avoid a Resteasy
    // routing collision: anything starting with /tenants/{tenantId}/partners must
    // resolve through this resource, otherwise the longest-prefix match goes wrong.

    @GET @Path("/{partnerId}/users")
    public List<AuthUser> listUsers(@PathParam("tenantId") Long tenantId,
                                    @PathParam("partnerId") Long partnerId) {
        return users.listByPartner(tenantId, partnerId);
    }

    @POST @Path("/{partnerId}/users")
    public AuthUser createUser(@PathParam("tenantId") Long tenantId,
                               @PathParam("partnerId") Long partnerId,
                               CreateUserRequest r) {
        AuthUser details = new AuthUser();
        details.firstName = r.firstName();
        details.lastName = r.lastName();
        details.phone = r.phone();
        return users.create(tenantId, partnerId, r.email(), r.password(), details);
    }
}
