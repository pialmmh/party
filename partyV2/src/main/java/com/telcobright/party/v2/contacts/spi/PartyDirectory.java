package com.telcobright.party.v2.contacts.spi;

import java.util.Optional;

/**
 * Maps a handle to a global person — party IS the directory. The normalizer
 * requires this port; the production impl (OdooPartyDirectory) wraps the Odoo
 * facade lookup, and tests hand in a fake.
 */
public interface PartyDirectory {

    /** A resolved secure-link person. {@code personId} is the global person key (a JSContact uid). */
    record PersonRef(String personId, String displayName) {}

    /**
     * Resolve a NORMALIZED handle to a secure-link person.
     *
     * @return the person, or empty for a non-user (the owner keeps the entry
     *         for invite; no global card is made).
     * @throws com.telcobright.party.v2.model.ProviderException if the directory
     *         is unreachable — a transient miss, NOT a non-user; the caller retries.
     */
    Optional<PersonRef> resolve(Handle handle);
}
