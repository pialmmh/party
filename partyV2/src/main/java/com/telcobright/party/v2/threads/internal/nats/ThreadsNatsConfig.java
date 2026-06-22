package com.telcobright.party.v2.threads.internal.nats;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Config for the thread-event feed publisher (mapped from
 * {@code party.v2.threads.nats.*}). Disabled by default — dev runs on the log
 * publisher until the it_vm broker is wanted. SL_THREADS on it_vm, additive,
 * never touching SL_CHAT or SL_CONTACTS (mirrors the contacts ruling).
 */
@ConfigMapping(prefix = "party.v2.threads.nats")
public interface ThreadsNatsConfig {

    @WithDefault("false")
    boolean enabled();

    @WithDefault("nats://10.10.185.1:4222")
    String url();

    @WithDefault("SL_THREADS")
    String stream();

    /** The per-account subject is {@code <subjectPrefix><ownerToken>}. */
    @WithDefault("sl.threads.")
    String subjectPrefix();

    @WithDefault("30")
    long maxAgeDays();
}
