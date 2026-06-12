package com.telcobright.party.v2.registration.internal.registry;

import com.telcobright.party.v2.registration.spi.DeviceRegistryStore;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The MySQL impl of the {@link DeviceRegistryStore} port (frozen §2).
 * Lazy schema init — operators that don't configure the registration
 * datasource must still boot party-api.
 */
@ApplicationScoped
public class JdbcDeviceRegistryStore implements DeviceRegistryStore {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS device_registry (
              device_id          VARCHAR(64)  PRIMARY KEY,
              partner_id         BIGINT       NOT NULL,
              e164               VARCHAR(20)  NOT NULL,
              status             VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
              refresh_token_hash CHAR(64)     NOT NULL,
              push_token         VARCHAR(255) NULL,
              last_seen          TIMESTAMP    NULL,
              created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
              INDEX ix_e164 (e164),
              INDEX ix_refresh (refresh_token_hash)
            )""";

    @Inject AgroalDataSource ds;

    /**
     * Lazy schema init (NOT a StartupEvent observer): operators that don't
     * configure the registration datasource must still boot party-api.
     */
    private volatile boolean schemaReady;

    private void ensureSchema() {
        if (schemaReady) return;
        synchronized (this) {
            if (schemaReady) return;
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute(DDL);
                schemaReady = true;
            } catch (SQLException e) {
                throw new IllegalStateException("device_registry schema init failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void upsertActive(String deviceId, long partnerId, String e164, String refreshTokenHash) {
        ensureSchema();
        String sql = """
                INSERT INTO device_registry (device_id, partner_id, e164, status, refresh_token_hash, last_seen)
                VALUES (?, ?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE partner_id = VALUES(partner_id), e164 = VALUES(e164),
                  status = 'ACTIVE', refresh_token_hash = VALUES(refresh_token_hash),
                  last_seen = CURRENT_TIMESTAMP""";
        execute(sql, st -> {
            st.setString(1, deviceId);
            st.setLong(2, partnerId);
            st.setString(3, e164);
            st.setString(4, refreshTokenHash);
        });
    }

    @Override
    public Optional<DeviceRow> findByRefreshTokenHash(String hash) {
        ensureSchema();
        return queryOne("SELECT * FROM device_registry WHERE refresh_token_hash = ?",
                st -> st.setString(1, hash));
    }

    @Override
    public Optional<DeviceRow> findByDeviceId(String deviceId) {
        ensureSchema();
        return queryOne("SELECT * FROM device_registry WHERE device_id = ?",
                st -> st.setString(1, deviceId));
    }

    @Override
    public List<DeviceRow> listByE164(String e164) {
        ensureSchema();
        List<DeviceRow> out = new ArrayList<>();
        String sql = "SELECT * FROM device_registry WHERE e164 = ? ORDER BY last_seen DESC";
        try (Connection c = ds.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, e164);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) out.add(toRow(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("device_registry list failed: " + e.getMessage(), e);
        }
        return out;
    }

    @Override
    public void rotateRefreshToken(String deviceId, String newHash) {
        execute("UPDATE device_registry SET refresh_token_hash = ?, last_seen = CURRENT_TIMESTAMP WHERE device_id = ?",
                st -> { st.setString(1, newHash); st.setString(2, deviceId); });
    }

    @Override
    public void setStatus(String deviceId, String status) {
        execute("UPDATE device_registry SET status = ? WHERE device_id = ?",
                st -> { st.setString(1, status); st.setString(2, deviceId); });
    }

    // ── internals ─────────────────────────────────────────────────────────

    private interface Binder { void bind(PreparedStatement st) throws SQLException; }

    private void execute(String sql, Binder binder) {
        try (Connection c = ds.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            binder.bind(st);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("device_registry write failed: " + e.getMessage(), e);
        }
    }

    private Optional<DeviceRow> queryOne(String sql, Binder binder) {
        try (Connection c = ds.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            binder.bind(st);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? Optional.of(toRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("device_registry read failed: " + e.getMessage(), e);
        }
    }

    private static DeviceRow toRow(ResultSet rs) throws SQLException {
        Timestamp seen = rs.getTimestamp("last_seen");
        return new DeviceRow(
                rs.getString("device_id"),
                rs.getLong("partner_id"),
                rs.getString("e164"),
                rs.getString("status"),
                rs.getString("refresh_token_hash"),
                rs.getString("push_token"),
                seen == null ? null : seen.toInstant());
    }
}
