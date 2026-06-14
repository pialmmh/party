package com.telcobright.party.v2.contacts.spi;

import java.util.List;

/**
 * One contact as it arrives — a phone-book row or a manual add — with its
 * handles still raw (un-normalized). The contract between the REST / import
 * layer and the normalizer. {@code rawHandles} hold phone numbers and emails
 * exactly as the device gave them; the normalizer cleans them. {@code fullName}
 * / {@code label} / {@code note} mirror the card the owner sees.
 */
public record RawContact(String fullName, List<String> rawHandles, String label, String note) {

    public RawContact {
        rawHandles = rawHandles == null ? List.of() : List.copyOf(rawHandles);
    }

    /** Convenience for a contact with no label/note (imports, tests). */
    public static RawContact of(String fullName, List<String> rawHandles) {
        return new RawContact(fullName, rawHandles, null, null);
    }
}
