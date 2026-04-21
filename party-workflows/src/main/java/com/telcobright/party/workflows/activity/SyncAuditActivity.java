package com.telcobright.party.workflows.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SyncAuditActivity {

    @ActivityMethod
    void markRunning(Long syncJobId, String runId);

    @ActivityMethod
    void markSuccess(Long syncJobId);

    @ActivityMethod
    void markFailed(Long syncJobId, String error);
}
