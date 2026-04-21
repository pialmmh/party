package com.telcobright.party.workflows;

import com.telcobright.party.workflows.activity.SyncAuditActivity;
import com.telcobright.party.workflows.activity.TenantProvisionActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class ProvisionTenantWorkflowImpl implements ProvisionTenantWorkflow {

    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(10))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(2))
                    .setMaximumAttempts(5)
                    .build())
            .build();

    private final TenantProvisionActivity provision =
            Workflow.newActivityStub(TenantProvisionActivity.class, activityOptions);
    private final SyncAuditActivity audit =
            Workflow.newActivityStub(SyncAuditActivity.class, activityOptions);

    @Override
    public void provision(Long tenantId, Long syncJobId) {
        audit.markRunning(syncJobId, Workflow.getInfo().getRunId());
        try {
            provision.createDatabase(tenantId);
            provision.applySchema(tenantId);
            provision.seedOwndb(tenantId);
            provision.seedDefaultRolesAndPermissions(tenantId);
            provision.markTenantActive(tenantId);
            audit.markSuccess(syncJobId);
        } catch (Exception ex) {
            audit.markFailed(syncJobId, ex.getMessage());
            throw ex;
        }
    }
}
