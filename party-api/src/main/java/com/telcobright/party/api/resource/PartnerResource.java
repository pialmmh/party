package com.telcobright.party.api.resource;

import com.telcobright.party.master.entity.Partner;
import com.telcobright.party.master.entity.PartnerExtra;
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

    @Inject PartnerService service;
    @Inject PartnerExtraService extraService;

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
}
