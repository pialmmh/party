package com.telcobright.party.api.resource;

import com.telcobright.party.domain.SyncStatus;
import com.telcobright.party.master.entity.TenantSyncJob;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@Path("/tenants/{tenantId}/sync-jobs")
@Produces(MediaType.APPLICATION_JSON)
public class SyncJobResource {

    @GET
    public List<TenantSyncJob> list(@PathParam("tenantId") Long tenantId,
                                    @QueryParam("status") SyncStatus status,
                                    @QueryParam("limit") @DefaultValue("50") int limit) {
        var query = (status == null)
                ? TenantSyncJob.find("tenantId = ?1 order by id desc", tenantId)
                : TenantSyncJob.find("tenantId = ?1 and status = ?2 order by id desc", tenantId, status);
        return query.page(0, limit).list();
    }

    @GET @Path("/{id}")
    public TenantSyncJob get(@PathParam("tenantId") Long tenantId, @PathParam("id") Long id) {
        TenantSyncJob j = TenantSyncJob.find("tenantId = ?1 and id = ?2", tenantId, id).firstResult();
        if (j == null) throw new NotFoundException();
        return j;
    }
}
