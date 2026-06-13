package com.telcobright.party.v2.contacts.internal.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.v2.contacts.publishes.ContactEvent.ContactCard;
import com.telcobright.party.v2.contacts.spi.ContactEntryStore;

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
 * The MySQL impl of {@link ContactEntryStore}. One {@code contact_entry} row per
 * (owner, contact); the version comes from a per-owner counter row
 * ({@code contact_entry_seq}) bumped with the LAST_INSERT_ID idiom — a plain row
 * X-lock serializes same-owner allocators with no gap locks (the §6 store learnt
 * this the hard way). The card is stored as JSON for the snapshot read.
 */
@ApplicationScoped
public class JdbcContactEntryStore implements ContactEntryStore {

    private static final int MAX_SEQ_RETRIES = 5;

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS contact_entry (
              owner_person_id VARCHAR(64) NOT NULL,
              contact_id      VARCHAR(64) NOT NULL,
              version         BIGINT      NOT NULL,
              content_hash    VARCHAR(64) NOT NULL,
              person_id       VARCHAR(64) NULL,
              card_json       JSON        NULL,
              deleted         TINYINT(1)  NOT NULL DEFAULT 0,
              created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (owner_person_id, contact_id),
              KEY ix_owner_version (owner_person_id, version)
            )""";

    private static final String DDL_SEQ = """
            CREATE TABLE IF NOT EXISTS contact_entry_seq (
              owner_person_id VARCHAR(64) PRIMARY KEY,
              seq             BIGINT      NOT NULL
            )""";

    @Inject AgroalDataSource ds;
    @Inject ObjectMapper json;

    private volatile boolean schemaReady;

    @Override
    public Optional<Entry> find(String ownerPersonId, String contactId) {
        ensureSchema();
        try (Connection c = ds.getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT content_hash, version, deleted FROM contact_entry "
                             + "WHERE owner_person_id = ? AND contact_id = ?")) {
            st.setString(1, ownerPersonId);
            st.setString(2, contactId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Entry(rs.getString("content_hash"),
                        rs.getLong("version"), rs.getBoolean("deleted")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("contact entry read failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long upsert(String ownerPersonId, String contactId, String contentHash,
                       String personId, ContactCard card) {
        ensureSchema();
        String cardJson = writeCard(card);
        return withSeqRetry(() -> upsertOnce(ownerPersonId, contactId, contentHash, personId, cardJson));
    }

    @Override
    public long tombstone(String ownerPersonId, String contactId) {
        ensureSchema();
        return withSeqRetry(() -> tombstoneOnce(ownerPersonId, contactId));
    }

    @Override
    public Snapshot snapshot(String ownerPersonId) {
        ensureSchema();
        List<SnapshotRow> rows = new ArrayList<>();
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT contact_id, version, person_id, card_json FROM contact_entry "
                            + "WHERE owner_person_id = ? AND deleted = 0 ORDER BY contact_id")) {
                st.setString(1, ownerPersonId);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new SnapshotRow(rs.getString("contact_id"), rs.getLong("version"),
                                rs.getString("person_id"), readCard(rs.getString("card_json"))));
                    }
                }
            }
            return new Snapshot(rows, highVersion(c, ownerPersonId));
        } catch (SQLException e) {
            throw new IllegalStateException("contact snapshot failed: " + e.getMessage(), e);
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private long upsertOnce(String owner, String contactId, String hash, String personId,
                            String cardJson) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                long seq = nextSeq(c, owner);
                try (PreparedStatement st = c.prepareStatement("""
                        INSERT INTO contact_entry
                          (owner_person_id, contact_id, version, content_hash, person_id, card_json, deleted)
                        VALUES (?, ?, ?, ?, ?, ?, 0)
                        ON DUPLICATE KEY UPDATE
                          version = VALUES(version), content_hash = VALUES(content_hash),
                          person_id = VALUES(person_id), card_json = VALUES(card_json), deleted = 0""")) {
                    st.setString(1, owner);
                    st.setString(2, contactId);
                    st.setLong(3, seq);
                    st.setString(4, hash);
                    st.setString(5, personId);
                    st.setString(6, cardJson);
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
        }
    }

    private long tombstoneOnce(String owner, String contactId) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                long seq = nextSeq(c, owner);
                try (PreparedStatement st = c.prepareStatement(
                        "UPDATE contact_entry SET version = ?, deleted = 1, "
                                + "content_hash = 'DELETED', person_id = NULL, card_json = NULL "
                                + "WHERE owner_person_id = ? AND contact_id = ?")) {
                    st.setLong(1, seq);
                    st.setString(2, owner);
                    st.setString(3, contactId);
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
        }
    }

    /** Counter-row allocation (deadlock-free): a plain row X-lock, no gap locks. */
    private static long nextSeq(Connection c, String owner) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(
                "INSERT INTO contact_entry_seq (owner_person_id, seq) VALUES (?, LAST_INSERT_ID(1)) "
                        + "ON DUPLICATE KEY UPDATE seq = LAST_INSERT_ID(seq + 1)")) {
            st.setString(1, owner);
            st.executeUpdate();
        }
        try (PreparedStatement st = c.prepareStatement("SELECT LAST_INSERT_ID()");
             ResultSet rs = st.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long highVersion(Connection c, String owner) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(
                "SELECT COALESCE(MAX(version), 0) FROM contact_entry WHERE owner_person_id = ?")) {
            st.setString(1, owner);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private interface SeqOp { long run() throws SQLException; }

    /** InnoDB can roll a deadlock victim (1213/40001) back cleanly; re-running re-allocates. */
    private long withSeqRetry(SeqOp op) {
        for (int attempt = 1; ; attempt++) {
            try {
                return op.run();
            } catch (SQLException e) {
                boolean deadlock = e.getErrorCode() == 1213 || "40001".equals(e.getSQLState());
                if (!deadlock || attempt >= MAX_SEQ_RETRIES) {
                    throw new IllegalStateException("contact entry write failed: " + e.getMessage(), e);
                }
            }
        }
    }

    private String writeCard(ContactCard card) {
        try {
            return json.writeValueAsString(card);
        } catch (Exception e) {
            throw new IllegalStateException("contact card serialize failed: " + e.getMessage(), e);
        }
    }

    private ContactCard readCard(String cardJson) {
        if (cardJson == null) return null;
        try {
            return json.readValue(cardJson, ContactCard.class);
        } catch (Exception e) {
            throw new IllegalStateException("contact card parse failed: " + e.getMessage(), e);
        }
    }

    private void ensureSchema() {
        if (schemaReady) return;
        synchronized (this) {
            if (schemaReady) return;
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute(DDL);
                st.execute(DDL_SEQ);
                schemaReady = true;
            } catch (SQLException e) {
                throw new IllegalStateException("contact entry schema init failed: " + e.getMessage(), e);
            }
        }
    }
}
