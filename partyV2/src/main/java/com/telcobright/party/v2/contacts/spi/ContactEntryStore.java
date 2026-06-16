package com.telcobright.party.v2.contacts.spi;

import com.telcobright.party.v2.contacts.publishes.ContactCard;

import java.util.List;
import java.util.Optional;

/**
 * The durable per-owner contact entries — the source of truth behind the feed.
 * It serves three reads of the SAME row: idempotency (the last content hash),
 * versioning (the owner's monotonic cursor), and the snapshot (the current
 * cards + their source). The production impl is a MySQL table; tests use the
 * in-memory impl.
 */
public interface ContactEntryStore {

    /** The idempotency + version state of one entry. */
    record Entry(String contentHash, long version, boolean deleted) {}

    /** One current entry in a snapshot. */
    record SnapshotRow(String contactId, long version, String personId, String source, ContactCard card) {}

    /** The owner's current book plus the cursor (high version, including tombstones). */
    record Snapshot(List<SnapshotRow> rows, long cursor) {}

    /**
     * One page of a snapshot (architect §7 — paged at {@code core.sync.batchSize}):
     * up to {@code limit} rows ordered by contactId, a keyset {@code nextCursor}
     * (the last row's contactId, or null on the last page), and {@code cursor} —
     * the owner's high version = the device's WS resume point.
     */
    record Page(List<SnapshotRow> rows, String nextCursor, long cursor) {}

    Optional<Entry> find(String ownerPersonId, String contactId);

    /** Store the card at the owner's NEXT version; clears any tombstone. @return the new version. */
    long upsert(String ownerPersonId, String contactId, String contentHash, String personId,
                String source, ContactCard card);

    /** Tombstone the entry at the owner's NEXT version (content erased). @return the new version. */
    long tombstone(String ownerPersonId, String contactId);

    /**
     * The current (non-tombstoned) book after {@code afterContactId} (null/empty =
     * first page), at most {@code limit} rows, ordered by contactId — keyset
     * pagination so a {@code >5000}-contact owner pages by cursor.
     */
    Page snapshotPage(String ownerPersonId, String afterContactId, int limit);

    /** The whole book in one shot — back-compat shim over {@link #snapshotPage}. */
    default Snapshot snapshot(String ownerPersonId) {
        Page page = snapshotPage(ownerPersonId, null, Integer.MAX_VALUE);
        return new Snapshot(page.rows(), page.cursor());
    }
}
