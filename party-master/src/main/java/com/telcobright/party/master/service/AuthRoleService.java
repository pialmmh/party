package com.telcobright.party.master.service;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.master.entity.AuthRole;
import com.telcobright.party.master.entity.AuthRolePermission;
import com.telcobright.party.master.entity.TenantSyncJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class AuthRoleService {

    @Inject TenantSyncJobService syncJobs;
    @Inject SyncDispatcher dispatcher;

    public List<AuthRole> listByTenant(Long tenantId) {
        return AuthRole.list("tenantId", tenantId);
    }

    public AuthRole findById(Long tenantId, Long id) {
        AuthRole r = AuthRole.find("tenantId = ?1 and id = ?2", tenantId, id).firstResult();
        if (r == null) throw new NotFoundException("role " + id + " not found");
        return r;
    }

    @Transactional
    public AuthRole create(Long tenantId, AuthRole r) {
        r.tenantId = tenantId;
        r.persist();
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_ROLE, String.valueOf(r.id), SyncOperation.CREATE);
        dispatcher.dispatch(job);
        return r;
    }

    @Transactional
    public AuthRole update(Long tenantId, Long id, AuthRole patch) {
        AuthRole r = findById(tenantId, id);
        if (patch.name != null) r.name = patch.name;
        if (patch.description != null) r.description = patch.description;
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_ROLE, String.valueOf(r.id), SyncOperation.UPDATE);
        dispatcher.dispatch(job);
        return r;
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        AuthRole r = findById(tenantId, id);
        r.delete();
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_ROLE, String.valueOf(id), SyncOperation.DELETE);
        dispatcher.dispatch(job);
    }

    @Transactional
    public void replacePermissions(Long tenantId, Long roleId, List<Long> permissionIds) {
        AuthRole r = findById(tenantId, roleId);
        AuthRolePermission.delete("roleId", r.id);
        for (Long pid : permissionIds) {
            AuthRolePermission link = new AuthRolePermission();
            link.roleId = r.id;
            link.permissionId = pid;
            link.tenantId = tenantId;
            link.persist();
        }
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.AUTH_ROLE_PERMISSION, String.valueOf(r.id), SyncOperation.UPDATE);
        dispatcher.dispatch(job);
    }
}
