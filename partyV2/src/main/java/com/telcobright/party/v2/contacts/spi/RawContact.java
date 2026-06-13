package com.telcobright.party.v2.contacts.spi;

import java.util.List;

/**
 * One contact as it arrives — a phone-book row or a manual add — with its
 * handles still raw (un-normalized). The contract between the REST / import
 * layer and the normalizer. {@code rawHandles} hold phone numbers and emails
 * exactly as the device gave them; the normalizer cleans them. {@code photo} is
 * an optional avatar reference.
 */
public record RawContact(String name, List<String> rawHandles, String petname,
                         List<String> groups, String photo) {

    public RawContact {
        rawHandles = rawHandles == null ? List.of() : List.copyOf(rawHandles);
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    /** Convenience for a contact with no petname/groups/photo (imports, tests). */
    public static RawContact of(String name, List<String> rawHandles) {
        return new RawContact(name, rawHandles, null, null, null);
    }
}
