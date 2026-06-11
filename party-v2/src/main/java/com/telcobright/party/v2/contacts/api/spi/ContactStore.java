package com.telcobright.party.v2.contacts.api.spi;

import java.util.List;
import java.util.Optional;

/**
 * The owner→contact graph port (frozen §6): one-directional rows, tombstoned
 * deletes, per-owner strictly-monotonic {@code seq} — every mutation allocates
 * the owner's next seq and stamps the row ATOMICALLY (one transaction in the
 * SQL impl). Production impl is the MySQL adapter (internal/store); tests hand
 * in an in-memory fake honouring the same seq contract.
 */
public interface ContactStore {

    record ContactRow(String ownerE164, String contactE164, String petname,
                      String state, long seq) {}

    String ACTIVE = "ACTIVE";
    String INVITED = "INVITED";
    String BLOCKED = "BLOCKED";
    String DELETED = "DELETED";

    /**
     * Upsert the row with the owner's next seq. {@code state} is always
     * concrete (callers resolve keep-state beforehand); a null petname
     * preserves the existing one when {@code keepExistingPetnameIfNull} is set
     * (block/delete must not clobber it). @return the new seq.
     */
    long upsertWithNextSeq(String owner, String contact, String petname,
                           String state, boolean keepExistingPetnameIfNull);

    Optional<ContactRow> find(String owner, String contact);

    /** Delta: ALL states including tombstones, strictly after {@code since}, seq order. */
    List<ContactRow> listSince(String owner, long since);

    /** Snapshot: current rows only (no tombstones), contact order. */
    List<ContactRow> snapshot(String owner);

    /** The owner's seq high-water mark (0 = no rows yet). */
    long highSeq(String owner);
}
