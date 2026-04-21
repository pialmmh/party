package com.telcobright.party.workflows;

import com.telcobright.party.master.entity.TenantSyncJob;
import com.telcobright.party.master.service.SyncDispatcher;
import io.quarkus.arc.properties.IfBuildProperty;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Overrides {@link com.telcobright.party.master.service.NoopSyncDispatcher} when temporal is enabled.
 * Present only if {@code party.temporal.enabled=true} at build time; the Noop default wins otherwise.
 */
@ApplicationScoped
@IfBuildProperty(name = "party.temporal.enabled", stringValue = "true")
public class TemporalSyncDispatcher implements SyncDispatcher {

    @ConfigProperty(name = "party.temporal.target") String target;
    @ConfigProperty(name = "party.task-queue.sync") String syncQueue;
    @ConfigProperty(name = "party.task-queue.critical") String criticalQueue;

    private WorkflowClient client;

    @PostConstruct
    void init() {
        var stubs = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
        client = WorkflowClient.newInstance(stubs);
    }

    @Override
    public void dispatch(TenantSyncJob job) {
        boolean isProvision = job.entityType.name().equals("TENANT_PROVISION");
        String queue = isProvision ? criticalQueue : syncQueue;
        var options = WorkflowOptions.newBuilder()
                .setTaskQueue(queue)
                .setWorkflowId(job.workflowId)
                .build();
        if (isProvision) {
            ProvisionTenantWorkflow wf = client.newWorkflowStub(ProvisionTenantWorkflow.class, options);
            WorkflowClient.start(wf::provision, job.tenantId, job.id);
        } else {
            SyncEntityWorkflow wf = client.newWorkflowStub(SyncEntityWorkflow.class, options);
            WorkflowClient.start(wf::sync, job.tenantId, job.entityType, job.entityId, job.operation, job.id);
        }
    }
}
