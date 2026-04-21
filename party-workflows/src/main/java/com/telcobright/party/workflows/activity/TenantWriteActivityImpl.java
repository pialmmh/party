package com.telcobright.party.workflows.activity;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.tenant.TenantProjectionWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TenantWriteActivityImpl implements TenantWriteActivity {

    @Inject TenantProjectionWriter writer;

    @Override
    public void apply(Long tenantId, EntityType type, SyncOperation op, String entityId) {
        writer.apply(tenantId, type, op, entityId);
    }
}
