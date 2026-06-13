package com.telcobright.party.v2.contacts.publishes;

import com.telcobright.party.v2.contacts.spi.Handle;

import java.util.List;

/**
 * One canonical change to one contact entry, dropped on the owner's per-account
 * feed — the frozen contact-event envelope. The device drains its feed by
 * {@code version} and applies each event into its ContactStore, the SAME path
 * a chat event takes.
 *
 * <ul>
 *   <li>{@code personId} is null when the contact is not (yet) a secure-link user.</li>
 *   <li>{@code card} is null on a delete.</li>
 *   <li>{@code version} is the owner's monotonic cursor; it advances only on a real change.</li>
 * </ul>
 */
public record ContactEvent(String type, String ownerPersonId, String contactId,
                           String personId, String source, long version, ContactCard card) {

    public static final String UPSERT = "contactUpsert";
    public static final String DELETE = "contactDelete";

    /**
     * The saved contact, JSContact (RFC 9553) field names — OUR payload schema
     * only, not the JMAP protocol (frozen shape, architect 2026-06-14).
     *
     * <ul>
     *   <li>{@code uid} = the global person key (a JSContact uid); equals the
     *       event's {@code personId}, or null for a non-user entry.</li>
     *   <li>{@code petname} = the owner's private name (client-precedence on merge).</li>
     *   <li>{@code photo} = an optional avatar reference; null when unknown.</li>
     * </ul>
     */
    public record ContactCard(String uid, String name, List<Handle> handles, String petname,
                              List<String> groups, String photo) {}

    public static ContactEvent upsert(String ownerPersonId, String contactId, String personId,
                                      String source, long version, ContactCard card) {
        return new ContactEvent(UPSERT, ownerPersonId, contactId, personId, source, version, card);
    }

    public static ContactEvent delete(String ownerPersonId, String contactId, String source,
                                      long version) {
        return new ContactEvent(DELETE, ownerPersonId, contactId, null, source, version, null);
    }
}
