package com.telcobright.party.tenant;

import com.telcobright.party.master.entity.Operator;
import com.telcobright.party.master.entity.Tenant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.flywaydb.core.Flyway;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Creates a new per-tenant database, applies the tenant baseline schema via Flyway,
 * and seeds the {@code owndb} identity row. Called by the ProvisionTenantWorkflow.
 *
 * Intentionally uses the MASTER JDBC URL (from PARTY_DB_URL) to issue CREATE DATABASE,
 * then points Flyway at the freshly-created DB using the tenant's own connection info.
 */
@ApplicationScoped
public class TenantProvisioner {

    private static final Logger LOG = Logger.getLogger(TenantProvisioner.class);

    @Inject TenantDataSourceRegistry dataSources;

    public void createDatabase(Long tenantId) {
        Tenant t = loadTenant(tenantId);
        String rootUrl = "jdbc:mariadb://" + t.dbHost + ":" + t.dbPort + "/";
        try (Connection c = DriverManager.getConnection(
                rootUrl,
                System.getenv().getOrDefault("PARTY_DB_ADMIN_USER", "root"),
                System.getenv().getOrDefault("PARTY_DB_ADMIN_PASS", ""));
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + t.dbName + "` CHARACTER SET utf8mb4");
            LOG.infof("Created tenant DB %s for tenant %d", t.dbName, tenantId);
        } catch (Exception e) {
            throw new RuntimeException("createDatabase failed for tenant " + tenantId, e);
        }
    }

    public void applySchema(Long tenantId) {
        var ds = dataSources.get(tenantId);
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration/tenant")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        LOG.infof("Applied tenant schema for tenant %d", tenantId);
    }

    public void seedOwndb(Long tenantId) {
        Tenant t = loadTenant(tenantId);
        Operator op = Operator.findById(t.operatorId);
        try (Connection c = dataSources.get(tenantId).getConnection();
             var ps = c.prepareStatement(
                     "INSERT INTO owndb (id, operator_id, operator_short, tenant_id, tenant_short) " +
                     "VALUES (1, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE operator_id=VALUES(operator_id), operator_short=VALUES(operator_short), " +
                     "tenant_id=VALUES(tenant_id), tenant_short=VALUES(tenant_short)")) {
            ps.setLong(1, op.id);
            ps.setString(2, op.shortName);
            ps.setLong(3, t.id);
            ps.setString(4, t.shortName);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("seedOwndb failed for tenant " + tenantId, e);
        }
    }

    public void seedDefaultRolesAndPermissions(Long tenantId) {
        try (Connection c = dataSources.get(tenantId).getConnection();
             Statement s = c.createStatement()) {
            // default roles
            s.executeUpdate(
                    "INSERT IGNORE INTO auth_role (id, name, description) VALUES " +
                    "(1, 'admin', 'Tenant administrator'), " +
                    "(2, 'reseller', 'Reseller-scoped access'), " +
                    "(3, 'agent', 'Call-center agent'), " +
                    "(4, 'viewer', 'Read-only access')");
            // default permissions
            s.executeUpdate(
                    "INSERT IGNORE INTO auth_permission (id, name, description) VALUES " +
                    "(1, 'partner:read:own', 'Read own partner data'), " +
                    "(2, 'partner:write:own', 'Write own partner data'), " +
                    "(3, 'user:read:tenant', 'Read any user in tenant'), " +
                    "(4, 'user:write:tenant', 'Write any user in tenant'), " +
                    "(5, 'role:manage:tenant', 'Manage roles and permissions')");
        } catch (Exception e) {
            throw new RuntimeException("seed defaults failed for tenant " + tenantId, e);
        }
    }

    @Transactional
    public void markTenantActive(Long tenantId) {
        Tenant t = loadTenant(tenantId);
        t.status = "ACTIVE";
    }

    private Tenant loadTenant(Long id) {
        Tenant t = Tenant.findById(id);
        if (t == null) throw new NotFoundException("tenant " + id + " not found");
        return t;
    }
}
