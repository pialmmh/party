package com.telcobright.party.v2.contacts.publishes;

import com.telcobright.party.v2.contacts.spi.Handle;

import java.util.List;

/**
 * Announced when a handle that was a non-user becomes a secure-link user. It
 * drives re-resolution: owners who saved this number as a non-user get a fresh
 * {@code contactUpsert} (source = rejoin), so the entry auto-links to the person.
 *
 * <p>The record + consumer contract are pinned now; the wiring from registration
 * is deferred (drift).
 */
public record UserJoined(String personId, List<Handle> handles) {}
