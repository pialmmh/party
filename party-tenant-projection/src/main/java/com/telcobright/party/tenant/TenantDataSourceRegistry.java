package com.telcobright.party.tenant;

import com.telcobright.party.master.entity.Tenant;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy per-tenant DataSource map. Tenant metadata (host, port, db, user, pass-ref)
 * is looked up from the master {@code tenant} table on first use.
 *
 * Passwords are resolved from environment variables whose name is stored in
 * {@code tenant.db_pass_ref} — we never store plaintext passwords in the DB.
 */
@ApplicationScoped
public class TenantDataSourceRegistry {

    private static final Logger LOG = Logger.getLogger(TenantDataSourceRegistry.class);

    private final Map<Long, AgroalDataSource> pools = new ConcurrentHashMap<>();

    public DataSource get(Long tenantId) {
        return pools.computeIfAbsent(tenantId, this::build);
    }

    public void evict(Long tenantId) {
        AgroalDataSource old = pools.remove(tenantId);
        if (old != null) old.close();
    }

    private AgroalDataSource build(Long tenantId) {
        Tenant t = Tenant.findById(tenantId);
        if (t == null) throw new NotFoundException("tenant " + tenantId + " not registered");

        String password = resolvePassword(t.dbPassRef);
        String url = "jdbc:mariadb://" + t.dbHost + ":" + t.dbPort + "/" + t.dbName;

        try {
            var cfg = new AgroalDataSourceConfigurationSupplier()
                    .metricsEnabled(false)
                    .connectionPoolConfiguration(cp -> cp
                            .maxSize(10)
                            .minSize(1)
                            .initialSize(1)
                            .acquisitionTimeout(Duration.ofSeconds(10))
                            .connectionFactoryConfiguration(cf -> cf
                                    .jdbcUrl(url)
                                    .principal(new NamePrincipal(t.dbUser))
                                    .credential(new SimplePassword(password))
                            ));
            AgroalDataSource ds = AgroalDataSource.from(cfg);
            LOG.infof("Initialized pool for tenant %d -> %s", tenantId, url);
            return ds;
        } catch (SQLException e) {
            throw new RuntimeException("could not build tenant DataSource for " + tenantId, e);
        }
    }

    private static String resolvePassword(String ref) {
        if (ref == null) return "";
        String v = System.getenv(ref);
        return v != null ? v : ref;
    }
}
