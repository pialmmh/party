package com.telcobright.party.v2.contacts.api.emit;

/**
 * Emitted on every contact-graph mutation for an owner; {@code seq} is the
 * owner's new high-water mark. Feeds the phase-2 XMPP nudge-to-own-JID so
 * other devices re-fetch the delta. (frozen §6 api/emit)
 */
public record ContactsChanged(String e164, long seq) {}
