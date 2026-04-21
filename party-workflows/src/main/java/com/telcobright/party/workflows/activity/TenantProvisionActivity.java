package com.telcobright.party.workflows.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface TenantProvisionActivity {

    @ActivityMethod
    void createDatabase(Long tenantId);

    @ActivityMethod
    void applySchema(Long tenantId);

    @ActivityMethod
    void seedOwndb(Long tenantId);

    @ActivityMethod
    void seedDefaultRolesAndPermissions(Long tenantId);

    @ActivityMethod
    void markTenantActive(Long tenantId);
}
