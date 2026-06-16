package com.telcobright.party.v2.sync;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.ConfigMapping.NamingStrategy;
import io.smallrye.config.WithDefault;

/**
 * Core sync knobs — the well-known config that crosses into the C++/TS client
 * core. ONE global batch size bounds BOTH the HTTP snapshot page and the WS
 * replay batch (architect frozen §7, RATIFIED 2026-06-17). Sized to the
 * average-max user (≤5000 contacts / messages) so the common case syncs in one
 * page / one batch.
 *
 * <p>Mapped from {@code core.sync.*} — deliberately OUTSIDE the {@code party.v2.*}
 * space so it is the SAME key name the client reads from the well-known config
 * (never hardcoded). When the config-manager well-known endpoint lands, only the
 * value source flips; the key stays.
 */
// VERBATIM so the member is the literal key `core.sync.batchSize` (NOT the kebab-case
// default `core.sync.batch-size`): §7 fixes the camelCase name the C++/TS client also reads.
@ConfigMapping(prefix = "core.sync", namingStrategy = NamingStrategy.VERBATIM)
public interface SyncConfig {

    /** Max contacts per snapshot page / messages per WS replay batch. */
    @WithDefault("5000")
    int batchSize();
}
