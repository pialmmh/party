package com.telcobright.party.master.service;

import com.telcobright.party.domain.EntityType;
import com.telcobright.party.domain.SyncOperation;
import com.telcobright.party.master.entity.Operator;
import com.telcobright.party.master.entity.Tenant;
import com.telcobright.party.master.entity.TenantSyncJob;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class TenantService {

    /** Apps the API will provision today. Single-app-per-tenant per design. */
    private static final Set<String> SUPPORTED_APPS = Set.of("orchestrix");

    @Inject TenantSyncJobService syncJobs;
    @Inject SyncDispatcher dispatcher;

    /** Operator slug bound at JVM startup via PartyProfileConfigSource. */
    @ConfigProperty(name = "party.operator.name") String boundOperatorName;

    public List<Tenant> listByOperator(Long operatorId) {
        return Tenant.list("operatorId", operatorId);
    }

    public List<Tenant> listForBoundOperator() {
        Operator op = boundOperator();
        return Tenant.list("operatorId", op.id);
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
        return createUnder(op, t);
    }

    @Transactional
    public Tenant createForBoundOperator(Tenant t) {
        return createUnder(boundOperator(), t);
    }

    private Tenant createUnder(Operator op, Tenant t) {
        if (t.appName == null || t.appName.isBlank()) t.appName = "orchestrix";
        if (!SUPPORTED_APPS.contains(t.appName)) {
            throw new BadRequestException("appName '" + t.appName + "' not supported. supported=" + SUPPORTED_APPS);
        }
        t.operatorId = op.id;
        if (t.status == null) t.status = "PROVISIONING";
        // Server-computed DB coordinates. Naming convention: <shortName>_<appName>.
        // Underscore-only so the identifier is valid in SQL without quoting.
        t.dbName = sanitize(t.shortName) + "_" + sanitize(t.appName);
        if (t.dbHost == null || t.dbHost.isBlank()) t.dbHost = "127.0.0.1";
        if (t.dbPort == null) t.dbPort = 3306;
        if (t.dbUser == null || t.dbUser.isBlank()) t.dbUser = "party_app";
        if (t.dbPassRef == null || t.dbPassRef.isBlank()) t.dbPassRef = "PARTY_TENANT_DB_PASS";
        t.persist();

        // enqueue provisioning audit + dispatch (NoopSyncDispatcher in dev — no real Temporal call)
        TenantSyncJob job = syncJobs.enqueue(t.id, EntityType.TENANT_PROVISION, String.valueOf(t.id), SyncOperation.PROVISION);
        dispatcher.dispatch(job);
        return t;
    }

    private Operator boundOperator() {
        Operator op = Operator.find("shortName", boundOperatorName).firstResult();
        if (op == null) {
            throw new NotFoundException(
                    "bound operator '" + boundOperatorName + "' (party.operator.name) not found in master DB. "
                  + "Re-run Flyway or insert the row.");
        }
        return op;
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
