package com.telcobright.party.v2.contacts.spi;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * A normalized way to reach a person — a phone or an email — with what it can
 * do. A handle is a FACADE of a person, never the person itself: one person
 * can hold several. tel values are E.164 ({@code +<digits>}); email values are
 * lower-cased. Defined once here and reused by {@link PartyDirectory} and the
 * published contact event.
 */
public record Handle(String kind, String value, List<String> capabilities) {

    public static final String TEL = "tel";
    public static final String EMAIL = "email";

    public static Handle tel(String e164) {
        return new Handle(TEL, e164, List.of("chat", "voice"));
    }

    public static Handle email(String address) {
        return new Handle(EMAIL, address, List.of("email"));
    }

    /** Derived — not serialized (the JSON payload carries only kind/value/capabilities). */
    @JsonIgnore
    public boolean isTel() {
        return TEL.equals(kind);
    }
}
