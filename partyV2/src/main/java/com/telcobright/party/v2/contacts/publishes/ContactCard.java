package com.telcobright.party.v2.contacts.publishes;

import com.telcobright.party.v2.contacts.spi.Handle;

import java.util.List;

/**
 * The saved contact — JSContact (RFC 9553) field names, OUR payload schema, the
 * canonical contact-event card (architect §8 canon 2026-06-14, reconciled with
 * the client store and the channel payload).
 *
 * <ul>
 *   <li>{@code uid} = the global person key (a JSContact uid); equals the event's
 *       {@code personId}, or null for a non-user entry.</li>
 *   <li>{@code fullName} = the contact's name; {@code label} = the owner's private
 *       name for them (client-precedence on merge); {@code note} = free text.</li>
 *   <li>{@code handles} = the ways to reach this person (phone now, email/jid later).</li>
 * </ul>
 */
public record ContactCard(String uid, String fullName, String label, String note,
                          List<Handle> handles) {}
