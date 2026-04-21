package com.telcobright.party.workflows;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ProvisionTenantWorkflow {

    @WorkflowMethod
    void provision(Long tenantId, Long syncJobId);
}
