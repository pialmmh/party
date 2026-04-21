package com.telcobright.party.workflows.activity;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface TenantWriteActivity {

    @ActivityMethod
    void apply(Long tenantId, EntityType type, SyncOperation op, String entityId);
}
