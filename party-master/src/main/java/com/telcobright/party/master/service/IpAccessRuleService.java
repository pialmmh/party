package com.telcobright.party.master.service;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.master.entity.IpAccessRule;
import com.telcobright.party.master.entity.TenantSyncJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class IpAccessRuleService {

    @Inject TenantSyncJobService syncJobs;
    @Inject SyncDispatcher dispatcher;

    public List<IpAccessRule> listForUser(Long tenantId, Long userId) {
        return IpAccessRule.list("tenantId = ?1 and userId = ?2", tenantId, userId);
    }

    @Transactional
    public IpAccessRule create(Long tenantId, Long userId, String ip, String permissionType) {
        IpAccessRule r = new IpAccessRule();
        r.tenantId = tenantId;
        r.userId = userId;
        r.ip = ip;
        r.permissionType = permissionType;
        r.persist();
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.IP_ACCESS_RULE, String.valueOf(r.id), SyncOperation.CREATE);
        dispatcher.dispatch(job);
        return r;
    }

    @Transactional
    public void delete(Long tenantId, Long id) {
        IpAccessRule.delete("tenantId = ?1 and id = ?2", tenantId, id);
        TenantSyncJob job = syncJobs.enqueue(tenantId, EntityType.IP_ACCESS_RULE, String.valueOf(id), SyncOperation.DELETE);
        dispatcher.dispatch(job);
    }
}
