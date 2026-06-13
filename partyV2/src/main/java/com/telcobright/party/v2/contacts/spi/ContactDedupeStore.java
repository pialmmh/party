package com.telcobright.party.v2.contacts.spi;

import java.util.Optional;

/**
 * The idempotency + version ledger: has this entry's content changed, and what
 * is the owner's next version. Keeps the contact feed idempotent — re-ingesting
 * the same content emits nothing, and {@code version} advances only on a real
 * change. The production impl is a small per-owner table; tests use the
 * in-memory impl.
 */
public interface ContactDedupeStore {

    /** The last published content hash for this entry, if any. */
    Optional<String> lastHash(String ownerPersonId, String contactId);

    /**
     * Record the new hash and return the owner's NEXT monotonic version.
     * Called only when the content actually changed.
     */
    long commit(String ownerPersonId, String contactId, String contentHash);
}
