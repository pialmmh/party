package com.telcobright.party.v2.threads.spi;

import java.util.List;
import java.util.Optional;

/**
 * The durable per-owner thread overlay — the source of truth behind the thread
 * feed. One row per (owner, conversationId): the four flags + the read marker +
 * the owner's monotonic {@code version}. Mirrors {@code ContactEntryStore}; the
 * production impl is a MySQL table, tests use the in-memory impl.
 *
 * <p>Unlike contacts, the snapshot INCLUDES tombstones (deleted=true rows) so a
 * late device can converge the hide (frozen §8b O5).
 */
public interface ThreadEntryStore {

    /** The current overlay of one thread (no conversationId — the caller has the key). */
    record State(boolean archived, boolean pinned, boolean muted, boolean deleted,
                 String readUpTo, long version) {

        /** A never-seen thread: all flags false, no read marker, version 0. */
        public static State empty() {
            return new State(false, false, false, false, null, 0L);
        }

        public State withArchived(boolean v) { return new State(v, pinned, muted, deleted, readUpTo, version); }
        public State withPinned(boolean v)   { return new State(archived, v, muted, deleted, readUpTo, version); }
        public State withMuted(boolean v)    { return new State(archived, pinned, v, deleted, readUpTo, version); }
        public State withDeleted(boolean v)  { return new State(archived, pinned, muted, v, readUpTo, version); }
        public State withReadUpTo(String v)  { return new State(archived, pinned, muted, deleted, v, version); }

        /** True when the OVERLAY (flags + read marker) is identical — version ignored. */
        public boolean sameOverlay(State o) {
            return archived == o.archived && pinned == o.pinned && muted == o.muted
                    && deleted == o.deleted && java.util.Objects.equals(readUpTo, o.readUpTo);
        }
    }

    /** One thread in a snapshot (the overlay + its key + version). */
    record SnapshotRow(String conversationId, boolean archived, boolean pinned, boolean muted,
                       boolean deleted, String readUpTo, long version) {}

    /**
     * One page of a snapshot (paged at {@code core.sync.batchSize}, frozen §7):
     * up to {@code limit} rows ordered by conversationId, a keyset {@code nextCursor}
     * (the last row's conversationId, or null on the last page), and {@code cursor}
     * = the owner's high version = the device's WS resume point.
     */
    record Page(List<SnapshotRow> rows, String nextCursor, long cursor) {}

    /** The current overlay of one thread, or empty when never touched. */
    Optional<State> find(String ownerPersonId, String conversationId);

    /**
     * Persist the full overlay at the owner's NEXT version.
     * @return the new version (the per-owner monotonic cursor).
     */
    long save(String ownerPersonId, String conversationId, boolean archived, boolean pinned,
              boolean muted, boolean deleted, String readUpTo);

    /**
     * One keyset page of the owner's threads after {@code afterConversationId}
     * (null/empty = first page), at most {@code limit} rows, ordered by
     * conversationId. Tombstones (deleted=true) ARE included (§8b O5).
     */
    Page snapshotPage(String ownerPersonId, String afterConversationId, int limit);
}
