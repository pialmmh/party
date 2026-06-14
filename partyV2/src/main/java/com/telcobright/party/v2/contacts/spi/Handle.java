package com.telcobright.party.v2.contacts.spi;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A normalized way to reach a person — a phone, an email, or a JID — with a
 * capability bitset (architect §8 canon: kind string + caps int). phone values
 * are E.164 ({@code +<digits>}); email values are lower-cased. A handle is a
 * FACADE of a person, never the person; one person can hold several. Defined
 * once here and reused by {@link PartyDirectory} and the published card.
 */
public record Handle(String kind, String value, int caps) {

    public static final String PHONE = "phone";
    public static final String EMAIL = "email";
    public static final String JID = "jid";

    /** Capability bits (architect §8): a handle advertises what it can do. */
    public static final int CHAT = 1;
    public static final int VOICE = 2;
    public static final int LOGIN = 4;

    public static Handle phone(String e164) {
        return new Handle(PHONE, e164, CHAT | VOICE);
    }

    public static Handle email(String address) {
        return new Handle(EMAIL, address, 0);   // email reaches, but is not a chat/voice/login handle
    }

    /** Derived — not serialized (the JSON payload carries only kind/value/caps). */
    @JsonIgnore
    public boolean isPhone() {
        return PHONE.equals(kind);
    }
}
