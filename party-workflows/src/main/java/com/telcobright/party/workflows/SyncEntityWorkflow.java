package com.telcobright.party.workflows;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface SyncEntityWorkflow {

    @WorkflowMethod
    void sync(Long tenantId, EntityType type, String entityId, SyncOperation op, Long syncJobId);
}
