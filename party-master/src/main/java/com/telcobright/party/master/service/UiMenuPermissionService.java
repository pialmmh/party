package com.telcobright.party.master.service;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.master.entity.TenantSyncJob;
import com.telcobright.party.master.entity.UiMenuPermission;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class UiMenuPermissionService {

    @Inject TenantSyncJobService syncJobs;
    @Inject SyncDispatcher dispatcher;

    public List<UiMenuPermission> listForUser(Long tenantId, Long userId) {
        return UiMenuPermission.list("tenantId = ?1 and userId = ?2", tenantId, userId);
    }

    @Transactional
    public UiMenuPermission upsert(Long tenantId, Long userId, String menuKey, String level) {
        UiMenuPermission existing = UiMenuPermission
                .find("tenantId = ?1 and userId = ?2 and menuKey = ?3", tenantId, userId, menuKey)
                .firstResult();
        boolean create = (existing == null);
        UiMenuPermission target = create ? new UiMenuPermission() : existing;
        target.tenantId = tenantId;
        target.userId = userId;
        target.menuKey = menuKey;
        target.permissionLevel = level;
        if (create) target.persist();
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.UI_MENU_PERMISSION, String.valueOf(target.id),
                create ? SyncOperation.CREATE : SyncOperation.UPDATE);
        dispatcher.dispatch(job);
        return target;
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        UiMenuPermission.delete("tenantId = ?1 and id = ?2", tenantId, id);
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.UI_MENU_PERMISSION, String.valueOf(id), SyncOperation.DELETE);
        dispatcher.dispatch(job);
    }
}
