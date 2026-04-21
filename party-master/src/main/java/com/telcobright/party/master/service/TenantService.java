package com.telcobright.party.master.service;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.master.entity.Operator;
import com.telcobright.party.master.entity.Tenant;
import com.telcobright.party.master.entity.TenantSyncJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class TenantService {

    @Inject TenantSyncJobService syncJobs;
    @Inject SyncDispatcher dispatcher;

    public List<Tenant> listByOperator(Long operatorId) {
        return Tenant.list("operatorId", operatorId);
    }

    public Tenant findById(Long id) {
        Tenant t = Tenant.findById(id);
        if (t == null) throw new NotFoundException("tenant " + id + " not found");
        return t;
    }

    @Transactional
    public Tenant create(Long operatorId, Tenant t) {
        Operator op = Operator.findById(operatorId);
        if (op == null) throw new NotFoundException("operator " + operatorId + " not found");
        t.operatorId = operatorId;
        if (t.status == null) t.status = "PROVISIONING";
        if (t.dbPort == null) t.dbPort = 3306;
        if (t.dbName == null) t.dbName = computeDbName(op.shortName, op.id, t.shortName);
        t.persist();

        // enqueue provisioning job
        TenantSyncJob job = syncJobs.enqueue(t.id, EntityType.TENANT_PROVISION, String.valueOf(t.id), SyncOperation.PROVISION);
        dispatcher.dispatch(job);

        // finalize db_name now that tenant.id is known (op-short + op-id + tenant-short + tenant-id)
        t.dbName = computeDbName(op.shortName, op.id, t.shortName) + "_" + t.id;
        return t;
    }

    @Transactional
    public Tenant update(Long id, Tenant patch) {
        Tenant t = findById(id);
        if (patch.shortName != null) t.shortName = patch.shortName;
        if (patch.fullName != null) t.fullName = patch.fullName;
        if (patch.companyName != null) t.companyName = patch.companyName;
        if (patch.address1 != null) t.address1 = patch.address1;
        if (patch.city != null) t.city = patch.city;
        if (patch.country != null) t.country = patch.country;
        if (patch.phone != null) t.phone = patch.phone;
        if (patch.email != null) t.email = patch.email;
        if (patch.status != null) t.status = patch.status;
        return t;
    }

    @Transactional
    public void delete(Long id) {
        Tenant t = findById(id);
        t.status = "DELETED";
    }

    private static String computeDbName(String opShort, Long opId, String tnShort) {
        // Naming convention per plan: {opShort}_{opId}_{tnShort}_{tnId}
        // tnId is appended after persist(); here we return the prefix for the pre-id computation.
        return sanitize(opShort) + "_" + opId + "_" + sanitize(tnShort);
    }

    private static String sanitize(String s) {
        return s == null ? "x" : s.toLowerCase().replaceAll("[^a-z0-9]", "_");
    }
}
