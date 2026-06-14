package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.publishes.ContactCard;
import com.telcobright.party.v2.contacts.spi.Handle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic keys for the contact feed: a stable per-entry {@code contactId}
 * (so re-importing the same handles maps to the same entry) and a
 * {@code contentHash} over everything that, if changed, must bump the version.
 */
final class ContactHashing {

    private ContactHashing() {}

    /** Stable id from the entry's normalized handles (already sorted). */
    static String contactId(List<Handle> sortedHandles) {
        String key = sortedHandles.stream().map(Handle::value).collect(Collectors.joining(","));
        return "c:" + sha256Hex(key).substring(0, 16);
    }

    /** Hash of the whole saved shape — uid, fullName, label, note, and every handle. */
    static String contentHash(ContactCard card) {
        String handles = card.handles().stream()
                .map(h -> h.kind() + ":" + h.value() + ":" + h.caps()).collect(Collectors.joining(","));
        String canonical = String.join("|",
                nz(card.uid()), nz(card.fullName()), nz(card.label()), nz(card.note()), handles);
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
