package com.telcobright.party.v2.contacts.internal.nats;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Config for the contact-event feed publisher (mapped from
 * {@code party.v2.contacts.nats.*}). Disabled by default — dev runs on the log
 * publisher until the it_vm broker is wanted. The architect ruling: SL_CONTACTS
 * on it_vm, additive, never touching SL_CHAT.
 */
@ConfigMapping(prefix = "party.v2.contacts.nats")
public interface ContactsNatsConfig {

    @WithDefault("false")
    boolean enabled();

    @WithDefault("nats://10.10.185.1:4222")
    String url();

    @WithDefault("SL_CONTACTS")
    String stream();

    /** The per-account subject is {@code <subjectPrefix><ownerToken>}. */
    @WithDefault("sl.contacts.")
    String subjectPrefix();

    @WithDefault("30")
    long maxAgeDays();
}
