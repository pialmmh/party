package com.telcobright.party.master.service;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.domain.SyncStatus;
import com.telcobright.party.master.entity.TenantSyncJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class TenantSyncJobService {

    @Transactional
    public TenantSyncJob enqueue(Long tenantId, EntityType type, String entityId, SyncOperation op) {
        TenantSyncJob job = new TenantSyncJob();
        job.tenantId = tenantId;
        job.entityType = type;
        job.entityId = entityId;
        job.operation = op;
        job.status = SyncStatus.PENDING;
        job.attempts = 0;
        job.workflowId = "sync-" + type.name().toLowerCase() + "-" + (entityId == null ? "na" : entityId)
                + "-" + op.name().toLowerCase() + "-" + System.currentTimeMillis();
        job.persist();
        return job;
    }

    @Transactional
    public void markRunning(Long jobId, String runId) {
        TenantSyncJob job = TenantSyncJob.findById(jobId);
        if (job == null) return;
        job.status = SyncStatus.RUNNING;
        job.runId = runId;
        job.startedAt = Instant.now();
        job.attempts += 1;
    }

    @Transactional
    public void markSuccess(Long jobId) {
        TenantSyncJob job = TenantSyncJob.findById(jobId);
        if (job == null) return;
        job.status = SyncStatus.SUCCESS;
        job.finishedAt = Instant.now();
    }

    @Transactional
    public void markFailed(Long jobId, String error) {
        TenantSyncJob job = TenantSyncJob.findById(jobId);
        if (job == null) return;
        job.status = SyncStatus.FAILED;
        job.error = error;
        job.finishedAt = Instant.now();
    }
}
