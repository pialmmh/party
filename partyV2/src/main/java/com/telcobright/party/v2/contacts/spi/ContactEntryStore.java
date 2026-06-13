package com.telcobright.party.v2.contacts.spi;

import com.telcobright.party.v2.contacts.publishes.ContactEvent.ContactCard;

import java.util.List;
import java.util.Optional;

/**
 * The durable per-owner contact entries — the source of truth behind the feed.
 * It serves three reads of the SAME row: idempotency (the last content hash),
 * versioning (the owner's monotonic cursor), and the snapshot (the current
 * cards). The production impl is a MySQL table; tests use the in-memory impl.
 */
public interface ContactEntryStore {

    /** The idempotency + version state of one entry. */
    record Entry(String contentHash, long version, boolean deleted) {}

    /** One current entry in a snapshot. */
    record SnapshotRow(String contactId, long version, String personId, ContactCard card) {}

    /** The owner's current book plus the cursor (high version, including tombstones). */
    record Snapshot(List<SnapshotRow> rows, long cursor) {}

    Optional<Entry> find(String ownerPersonId, String contactId);

    /** Store the card at the owner's NEXT version; clears any tombstone. @return the new version. */
    long upsert(String ownerPersonId, String contactId, String contentHash, String personId,
                ContactCard card);

    /** Tombstone the entry at the owner's NEXT version (content erased). @return the new version. */
    long tombstone(String ownerPersonId, String contactId);

    Snapshot snapshot(String ownerPersonId);
}
