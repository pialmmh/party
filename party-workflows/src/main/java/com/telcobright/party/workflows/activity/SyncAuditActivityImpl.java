package com.telcobright.party.workflows.activity;

import com.telcobright.party.master.service.TenantSyncJobService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SyncAuditActivityImpl implements SyncAuditActivity {

    @Inject TenantSyncJobService syncJobs;

    @Override public void markRunning(Long syncJobId, String runId) { syncJobs.markRunning(syncJobId, runId); }
    @Override public void markSuccess(Long syncJobId)               { syncJobs.markSuccess(syncJobId); }
    @Override public void markFailed(Long syncJobId, String error)  { syncJobs.markFailed(syncJobId, error); }
}
