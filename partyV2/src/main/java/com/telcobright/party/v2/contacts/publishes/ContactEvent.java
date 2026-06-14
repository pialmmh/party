package com.telcobright.party.v2.contacts.publishes;

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
 * </ul>
 */
public record ContactEvent(String type, String contactId, String personId, String source,
                           long version, ContactCard card) {

    public static final String UPSERT = "contactUpsert";
    public static final String DELETE = "contactDelete";

    public static ContactEvent upsert(String contactId, String personId, String source,
                                      long version, ContactCard card) {
        return new ContactEvent(UPSERT, contactId, personId, source, version, card);
    }

    public static ContactEvent delete(String contactId, String source, long version) {
        return new ContactEvent(DELETE, contactId, null, source, version, null);
    }
}
