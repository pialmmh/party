package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.publishes.ContactEvent.ContactCard;
import com.telcobright.party.v2.contacts.spi.Handle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

/**
 * Deterministic keys for the contact feed: a stable per-entry {@code contactId}
 * (so re-importing the same handles maps to the same entry) and a
 * {@code contentHash} over everything that, if changed, must bump the version.
 */
final class ContactHashing {

    private ContactHashing() {}

    /** Stable id from the entry's normalized handles (already sorted). */
    static String contactId(java.util.List<Handle> sortedHandles) {
        String key = sortedHandles.stream().map(Handle::value).collect(Collectors.joining(","));
        return "c:" + sha256Hex(key).substring(0, 16);
    }

    /** Hash of the whole saved shape — name, handles, petname, groups, resolved person. */
    static String contentHash(ContactCard card, String personId) {
        String handles = card.handles().stream()
                .map(h -> h.kind() + ":" + h.value()).collect(Collectors.joining(","));
        String groups = card.groups().stream().sorted().collect(Collectors.joining(","));
        String canonical = String.join("|",
                nz(card.name()), handles, nz(card.petname()), groups, nz(personId));
        return sha256Hex(canonical);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                   .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);   // every JVM ships it
        }
    }
}
