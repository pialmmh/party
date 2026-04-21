package com.telcobright.party.master.service;

import com.telcobright.party.master.entity.TenantSyncJob;

/**
 * Pluggable strategy for starting the Temporal workflow that replicates a master write to the tenant DB.
 * In production, the party-workflows module provides an implementation that starts a Temporal workflow.
 * In tests, {@link NoopSyncDispatcher} is used and jobs remain PENDING.
 */
public interface SyncDispatcher {
    void dispatch(TenantSyncJob job);
}
