package com.telcobright.party.master.service;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.master.entity.AuthPermission;
import com.telcobright.party.master.entity.TenantSyncJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class AuthPermissionService {

    @Inject TenantSyncJobService syncJobs;
    @Inject SyncDispatcher dispatcher;

    public List<AuthPermission> listByTenant(Long tenantId) {
        return AuthPermission.list("tenantId", tenantId);
    }

    public AuthPermission findById(Long tenantId, Long id) {
        AuthPermission p = AuthPermission.find("tenantId = ?1 and id = ?2", tenantId, id).firstResult();
        if (p == null) throw new NotFoundException("permission " + id + " not found");
        return p;
    }

    @Transactional
    public AuthPermission create(Long tenantId, AuthPermission p) {
        p.tenantId = tenantId;
        p.persist();
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_PERMISSION, String.valueOf(p.id), SyncOperation.CREATE);
        dispatcher.dispatch(job);
        return p;
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        AuthPermission p = findById(tenantId, id);
        p.delete();
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_PERMISSION, String.valueOf(id), SyncOperation.DELETE);
        dispatcher.dispatch(job);
    }
}
