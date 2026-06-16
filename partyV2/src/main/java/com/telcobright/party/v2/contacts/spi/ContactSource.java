package com.telcobright.party.v2.contacts.spi;

/**
 * Where a contact change came from — the fan-in producers that all funnel
 * through the one normalizer. Carried on the published event as a lower-case
 * wire string.
 */
public enum ContactSource {
    PHONEBOOK("phonebook"),
    MANUAL("manual"),
    REJOIN("rejoin"),
    EMAIL_HARVEST("emailHarvest");

    private final String wire;

    ContactSource(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    /** Parse the lower-case wire string (the {@code ?source=} param) back to a source. */
    public static ContactSource from(String wire) {
        for (ContactSource s : values()) {
            if (s.wire.equals(wire)) return s;
        }
        throw new IllegalArgumentException("unknown contact source: " + wire);
    }
}
