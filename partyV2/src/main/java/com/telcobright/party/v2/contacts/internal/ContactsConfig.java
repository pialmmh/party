package com.telcobright.party.v2.contacts.internal;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Config for the contacts feature (frozen §6).
 * Mapped from {@code party.v2.contacts.*}.
 */
@ConfigMapping(prefix = "party.v2.contacts")
public interface ContactsConfig {

    Invites invites();

    /**
     * Dev-mode owner identity: accept {@code X-SL-Account: <e164>} when no
     * Bearer token is presented (matches the auth-less portal ruling).
     * Flip off once clients all send the §2 device JWT.
     */
    @WithDefault("true")
    boolean ownerHeaderEnabled();

    interface Invites {
        /** Dev mode logs the invite server-side; SMS gateway later (we ARE the operator). */
        @WithDefault("false")
        boolean devMode();
    }
}
