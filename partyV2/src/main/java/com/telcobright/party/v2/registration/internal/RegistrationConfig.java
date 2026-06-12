package com.telcobright.party.v2.registration.internal;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Config for the secure-link registration feature (frozen-interfaces §2).
 * Mapped from {@code party.v2.registration.*}.
 */
@ConfigMapping(prefix = "party.v2.registration")
public interface RegistrationConfig {

    /** Which configured tenant's Odoo backend holds the facade model. */
    @WithDefault("t1")
    String tenant();

    Otp otp();
    Jwt jwt();
    Xmpp xmpp();
    Entitlement entitlement();
    Ejabberd ejabberd();
    Device device();

    interface Otp {
        /** Dev mode: log the code server-side instead of sending SMS. The API response NEVER carries it. */
        @WithDefault("false")
        boolean devMode();

        @WithDefault("300")
        int ttlSeconds();

        @WithDefault("3")
        int maxAttempts();
    }

    interface Jwt {
        /**
         * Path to the HS256 shared-key file (same key configured in ejabberd
         * jwt_key — byte-identical, so create it WITHOUT a trailing newline).
         * Never in git. Optional so operators that don't run registration
         * still boot; minting fails fast with a clear message if unset.
         */
        Optional<String> secretFile();

        /** Access-token TTL — short by design; refresh is the long-lived leg. */
        @WithDefault("900")
        int ttlSeconds();

        @WithDefault("party")
        String issuer();
    }

    interface Xmpp {
        /** The vhost in the JID — must match Odoo's secure_link.xmpp_vhost and ejabberd hosts. */
        @WithDefault("localhost")
        String domain();

        String host();

        @WithDefault("5222")
        int port();
    }

    interface Entitlement {
        /** OFF until the RTC-Manager endpoint is agreed (frozen §2). */
        @WithDefault("false")
        boolean enforce();

        Optional<String> baseUrl();
    }

    interface Ejabberd {
        /** OFF until mod_http_api is enabled on the box (task: ejabberd JWT config). */
        @WithDefault("false")
        boolean kickEnabled();

        Optional<String> apiUrl();
    }

    interface Device {
        /**
         * Stage-1 of the two-stage inactivity expiry (Conversations pattern):
         * a device whose last_seen is older than this refuses refresh and
         * flips to EXPIRED (re-OTP required). Stage-2 archival never deletes.
         */
        @WithDefault("30")
        int inactivityExpireDays();
    }
}
