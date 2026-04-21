package com.telcobright.party.workflows;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.workflows.activity.SyncAuditActivity;
import com.telcobright.party.workflows.activity.TenantWriteActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class SyncEntityWorkflowImpl implements SyncEntityWorkflow {

    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setBackoffCoefficient(2.0)
                    .setMaximumInterval(Duration.ofMinutes(5))
                    .setMaximumAttempts(12)
                    .build())
            .build();

    private final TenantWriteActivity tenantWrite =
            Workflow.newActivityStub(TenantWriteActivity.class, activityOptions);
    private final SyncAuditActivity audit =
            Workflow.newActivityStub(SyncAuditActivity.class, activityOptions);

    @Override
    public void sync(Long tenantId, EntityType type, String entityId, SyncOperation op, Long syncJobId) {
        audit.markRunning(syncJobId, Workflow.getInfo().getRunId());
        try {
            tenantWrite.apply(tenantId, type, op, entityId);
            audit.markSuccess(syncJobId);
        } catch (Exception ex) {
            audit.markFailed(syncJobId, ex.getMessage());
            throw ex;
        }
    }
}
