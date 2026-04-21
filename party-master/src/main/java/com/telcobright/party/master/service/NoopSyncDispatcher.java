package com.telcobright.party.master.service;

import com.telcobright.party.master.entity.TenantSyncJob;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
@DefaultBean
public class NoopSyncDispatcher implements SyncDispatcher {

    private static final Logger LOG = Logger.getLogger(NoopSyncDispatcher.class);

    @Override
    public void dispatch(TenantSyncJob job) {
        LOG.debugf("[NOOP] would start workflow %s for job=%d tenant=%d entity=%s op=%s",
                job.workflowId, job.id, job.tenantId, job.entityType, job.operation);
    }
}
