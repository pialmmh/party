package com.telcobright.party.workflows.activity;

import com.telcobright.party.tenant.TenantProvisioner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TenantProvisionActivityImpl implements TenantProvisionActivity {

    @Inject TenantProvisioner provisioner;

    @Override public void createDatabase(Long tenantId) { provisioner.createDatabase(tenantId); }
    @Override public void applySchema(Long tenantId) { provisioner.applySchema(tenantId); }
    @Override public void seedOwndb(Long tenantId) { provisioner.seedOwndb(tenantId); }
    @Override public void seedDefaultRolesAndPermissions(Long tenantId) { provisioner.seedDefaultRolesAndPermissions(tenantId); }
    @Override public void markTenantActive(Long tenantId) { provisioner.markTenantActive(tenantId); }
}
