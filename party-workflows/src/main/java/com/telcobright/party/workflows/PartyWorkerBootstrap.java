package com.telcobright.party.workflows;

import com.telcobright.party.workflows.activity.SyncAuditActivity;
import com.telcobright.party.workflows.activity.TenantProvisionActivity;
import com.telcobright.party.workflows.activity.TenantWriteActivity;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Starts a Temporal worker registered on all three task queues. Gated by
 * {@code party.temporal.enabled=true}; disabled by default so tests run
 * without needing a Temporal server.
 */
@ApplicationScoped
@IfBuildProperty(name = "party.temporal.enabled", stringValue = "true")
public class PartyWorkerBootstrap {

    private static final Logger LOG = Logger.getLogger(PartyWorkerBootstrap.class);

    @ConfigProperty(name = "party.temporal.target") String target;
    @ConfigProperty(name = "party.temporal.namespace") String namespace;
    @ConfigProperty(name = "party.task-queue.sync") String syncQueue;
    @ConfigProperty(name = "party.task-queue.bulk") String bulkQueue;
    @ConfigProperty(name = "party.task-queue.critical") String criticalQueue;

    @Inject SyncAuditActivity syncAudit;
    @Inject TenantWriteActivity tenantWrite;
    @Inject TenantProvisionActivity tenantProvision;

    private WorkflowServiceStubs service;
    private WorkerFactory factory;

    void onStart(@Observes StartupEvent ev) {
        LOG.infof("Starting Temporal worker target=%s ns=%s", target, namespace);
        service = WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(target)
                        .setEnableHttps(false)
                        .build());
        WorkflowClient client = WorkflowClient.newInstance(service);
        factory = WorkerFactory.newInstance(client);

        for (String q : new String[]{syncQueue, bulkQueue, criticalQueue}) {
            var worker = factory.newWorker(q);
            worker.registerWorkflowImplementationTypes(SyncEntityWorkflowImpl.class, ProvisionTenantWorkflowImpl.class);
            worker.registerActivitiesImplementations(syncAudit, tenantWrite, tenantProvision);
        }
        factory.start();
        LOG.info("Temporal worker started on: " + syncQueue + ", " + bulkQueue + ", " + criticalQueue);
    }

    void onStop(@Observes ShutdownEvent ev) {
        if (factory != null) factory.shutdown();
        if (service != null) service.shutdown();
    }
}
