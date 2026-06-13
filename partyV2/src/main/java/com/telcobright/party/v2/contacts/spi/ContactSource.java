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
}
