package com.telcobright.party.v2.contacts.publishes;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One canonical change to one contact entry, dropped on the owner's per-account
 * feed — the CANONICAL contact-event wire JSON (architect §8 canon 2026-06-14,
 * = the client parse shape). Subject {@code sl.contacts.<ownerPersonId>} carries
 * the owner, so the owner is NOT in the body (the device stamps it from its own
 * account). The device drains its feed by {@code version} and applies each event
 * into its ContactStore — the SAME path a chat event takes.
 *
 * <ul>
 *   <li>{@code type} = {@code contactUpsert} | {@code contactDelete}.</li>
 *   <li>{@code personId} is null when the contact is not (yet) a secure-link user;
 *       {@code card} is null on a delete.</li>
 *   <li>{@code version} is the owner's monotonic cursor; it advances only on a real change.</li>
 *   <li>{@code originId} = the OPTIONAL client reconcile key (§8 RULING B): echoed
 *       from the write so the originating device matches this live event back to its
 *       optimistic row. OMITTED from the wire when null (additive — existing events
 *       stay byte-identical; a delete carries none).</li>
 * </ul>
 */
public record ContactEvent(String type, String contactId, String personId, String source,
                           long version, ContactCard card,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String originId) {

    public static final String UPSERT = "contactUpsert";
    public static final String DELETE = "contactDelete";

    /** Upsert carrying the client reconcile key (null when the producer minted none). */
    public static ContactEvent upsert(String contactId, String personId, String source,
                                      long version, ContactCard card, String originId) {
        return new ContactEvent(UPSERT, contactId, personId, source, version, card, originId);
    }

    /** Upsert with no reconcile key (server-side / legacy producers). */
    public static ContactEvent upsert(String contactId, String personId, String source,
                                      long version, ContactCard card) {
        return upsert(contactId, personId, source, version, card, null);
    }

    public static ContactEvent delete(String contactId, String source, long version) {
        return new ContactEvent(DELETE, contactId, null, source, version, null, null);
    }
}
