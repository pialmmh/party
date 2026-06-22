package com.telcobright.party.v2.threads.internal.store;

import com.telcobright.party.v2.threads.spi.ThreadEntryStore;

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
 * The MySQL impl of {@link ThreadEntryStore}. One {@code thread_entry} row per
 * (owner, conversation); the version comes from a per-owner counter row
 * ({@code thread_entry_seq}) bumped with the LAST_INSERT_ID idiom — a plain row
 * X-lock serializes same-owner allocators with no gap locks (the contacts store
 * learnt this the hard way). Tombstones (deleted=1) stay in the table and in the
 * snapshot (§8b O5). Mirrors JdbcContactEntryStore.
 */
@ApplicationScoped
public class JdbcThreadEntryStore implements ThreadEntryStore {

    private static final int MAX_SEQ_RETRIES = 5;

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS thread_entry (
              owner_person_id VARCHAR(64)  NOT NULL,
              conversation_id VARCHAR(255) NOT NULL,
              version         BIGINT       NOT NULL,
              archived        TINYINT(1)   NOT NULL DEFAULT 0,
              pinned          TINYINT(1)   NOT NULL DEFAULT 0,
              muted           TINYINT(1)   NOT NULL DEFAULT 0,
              deleted         TINYINT(1)   NOT NULL DEFAULT 0,
              read_up_to      VARCHAR(64)  NULL,
              created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (owner_person_id, conversation_id),
              KEY ix_owner_version (owner_person_id, version)
            )""";

    private static final String DDL_SEQ = """
            CREATE TABLE IF NOT EXISTS thread_entry_seq (
              owner_person_id VARCHAR(64) PRIMARY KEY,
              seq             BIGINT      NOT NULL
            )""";

    @Inject AgroalDataSource ds;

    private volatile boolean schemaReady;

    @Override
    public Optional<State> find(String ownerPersonId, String conversationId) {
        ensureSchema();
        try (Connection c = ds.getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT archived, pinned, muted, deleted, read_up_to, version FROM thread_entry "
                             + "WHERE owner_person_id = ? AND conversation_id = ?")) {
            st.setString(1, ownerPersonId);
            st.setString(2, conversationId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new State(rs.getBoolean("archived"), rs.getBoolean("pinned"),
                        rs.getBoolean("muted"), rs.getBoolean("deleted"),
                        rs.getString("read_up_to"), rs.getLong("version")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("thread entry read failed: " + e.getMessage(), e);
        }
    }

    @Override
    public long save(String ownerPersonId, String conversationId, boolean archived, boolean pinned,
                     boolean muted, boolean deleted, String readUpTo) {
        ensureSchema();
        return withSeqRetry(() -> saveOnce(ownerPersonId, conversationId, archived, pinned, muted, deleted, readUpTo));
    }

    @Override
    public Page snapshotPage(String ownerPersonId, String afterConversationId, int limit) {
        ensureSchema();
        String after = afterConversationId == null ? "" : afterConversationId;
        long fetch = (long) limit + 1;   // +1 sentinel row reveals a next page
        List<SnapshotRow> rows = new ArrayList<>();
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT conversation_id, archived, pinned, muted, deleted, read_up_to, version "
                            + "FROM thread_entry WHERE owner_person_id = ? AND conversation_id > ? "
                            + "ORDER BY conversation_id LIMIT ?")) {
                st.setString(1, ownerPersonId);
                st.setString(2, after);
                st.setLong(3, fetch);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new SnapshotRow(rs.getString("conversation_id"), rs.getBoolean("archived"),
                                rs.getBoolean("pinned"), rs.getBoolean("muted"), rs.getBoolean("deleted"),
                                rs.getString("read_up_to"), rs.getLong("version")));
                    }
                }
            }
            boolean more = rows.size() > limit;
            if (more) rows = rows.subList(0, limit);
            String nextCursor = more ? rows.get(rows.size() - 1).conversationId() : null;
            return new Page(List.copyOf(rows), nextCursor, highVersion(c, ownerPersonId));
        } catch (SQLException e) {
            throw new IllegalStateException("thread snapshot failed: " + e.getMessage(), e);
        }
    }

    // ── internals ─────────────────────────────────────────────────────────

    private long saveOnce(String owner, String conversationId, boolean archived, boolean pinned,
                          boolean muted, boolean deleted, String readUpTo) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                long seq = nextSeq(c, owner);
                try (PreparedStatement st = c.prepareStatement("""
                        INSERT INTO thread_entry
                          (owner_person_id, conversation_id, version, archived, pinned, muted, deleted, read_up_to)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          version = VALUES(version), archived = VALUES(archived), pinned = VALUES(pinned),
                          muted = VALUES(muted), deleted = VALUES(deleted), read_up_to = VALUES(read_up_to)""")) {
                    st.setString(1, owner);
                    st.setString(2, conversationId);
                    st.setLong(3, seq);
                    st.setBoolean(4, archived);
                    st.setBoolean(5, pinned);
                    st.setBoolean(6, muted);
                    st.setBoolean(7, deleted);
                    st.setString(8, readUpTo);
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
                "INSERT INTO thread_entry_seq (owner_person_id, seq) VALUES (?, LAST_INSERT_ID(1)) "
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
                "SELECT COALESCE(MAX(version), 0) FROM thread_entry WHERE owner_person_id = ?")) {
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
                    throw new IllegalStateException("thread entry write failed: " + e.getMessage(), e);
                }
            }
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
                throw new IllegalStateException("thread entry schema init failed: " + e.getMessage(), e);
            }
        }
    }
}
