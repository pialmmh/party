package com.telcobright.party.v2.contacts.internal.store;

import com.telcobright.party.v2.contacts.api.spi.ContactStore;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The MySQL impl of the {@link ContactStore} port (frozen §6). The next seq is
 * allocated and stamped IN ONE TRANSACTION — SELECT … FOR UPDATE on the
 * (owner_e164, seq) index serializes concurrent allocators for the same owner.
 */
@ApplicationScoped
public class JdbcContactStore implements ContactStore {

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS contact (
              owner_e164   VARCHAR(20)  NOT NULL,
              contact_e164 VARCHAR(20)  NOT NULL,
              petname      VARCHAR(120) NULL,
              state        ENUM('ACTIVE','INVITED','BLOCKED','DELETED') NOT NULL,
              seq          BIGINT       NOT NULL,
              created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (owner_e164, contact_e164),
              KEY ix_owner_seq (owner_e164, seq)
            )""";

    @Inject AgroalDataSource ds;

    private volatile boolean schemaReady;

    @Override
    public long upsertWithNextSeq(String owner, String contact, String petname,
                                  String state, boolean keepExistingPetnameIfNull) {
        ensureSchema();
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                long seq = nextSeq(c, owner);
                String sql = """
                        INSERT INTO contact (owner_e164, contact_e164, petname, state, seq)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          petname = %s,
                          state = VALUES(state),
                          seq = VALUES(seq)"""
                        .formatted(keepExistingPetnameIfNull
                                ? "COALESCE(VALUES(petname), petname)" : "VALUES(petname)");
                try (PreparedStatement st = c.prepareStatement(sql)) {
                    st.setString(1, owner);
                    st.setString(2, contact);
                    st.setString(3, petname);
                    st.setString(4, state);
                    st.setLong(5, seq);
                    st.executeUpdate();
                }
                c.commit();
                return seq;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("contact upsert failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ContactRow> find(String owner, String contact) {
        ensureSchema();
        return queryOne("SELECT * FROM contact WHERE owner_e164 = ? AND contact_e164 = ?",
                st -> { st.setString(1, owner); st.setString(2, contact); });
    }

    @Override
    public List<ContactRow> listSince(String owner, long since) {
        ensureSchema();
        return queryMany(
                "SELECT * FROM contact WHERE owner_e164 = ? AND seq > ? ORDER BY seq",
                st -> { st.setString(1, owner); st.setLong(2, since); });
    }

    @Override
    public List<ContactRow> snapshot(String owner) {
        ensureSchema();
        return queryMany(
                "SELECT * FROM contact WHERE owner_e164 = ? AND state <> 'DELETED' ORDER BY contact_e164",
                st -> st.setString(1, owner));
    }

    @Override
    public long highSeq(String owner) {
        ensureSchema();
        try (Connection c = ds.getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT COALESCE(MAX(seq), 0) FROM contact WHERE owner_e164 = ?")) {
            st.setString(1, owner);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("contact high-seq read failed: " + e.getMessage(), e);
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private static long nextSeq(Connection c, String owner) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(
                "SELECT COALESCE(MAX(seq), 0) + 1 FROM contact WHERE owner_e164 = ? FOR UPDATE")) {
            st.setString(1, owner);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void ensureSchema() {
        if (schemaReady) return;
        synchronized (this) {
            if (schemaReady) return;
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute(DDL);
                schemaReady = true;
            } catch (SQLException e) {
                throw new IllegalStateException("contact schema init failed: " + e.getMessage(), e);
            }
        }
    }

    private interface Binder { void bind(PreparedStatement st) throws SQLException; }

    private Optional<ContactRow> queryOne(String sql, Binder binder) {
        List<ContactRow> rows = queryMany(sql, binder);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private List<ContactRow> queryMany(String sql, Binder binder) {
        List<ContactRow> out = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            binder.bind(st);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    out.add(new ContactRow(
                            rs.getString("owner_e164"),
                            rs.getString("contact_e164"),
                            rs.getString("petname"),
                            rs.getString("state"),
                            rs.getLong("seq")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("contact read failed: " + e.getMessage(), e);
        }
        return out;
    }
}
