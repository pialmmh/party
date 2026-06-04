package com.telcobright.party.v2.registration.internal;

/**
 * Mints the short-TTL per-device access token (the {@code xmppCredential}).
 *
 * Frozen §2: party mints HS256 day one ({@link HmacTokenMinter}); Keycloak
 * arrives LATER as the minter only — swapping this implementation (and
 * ejabberd's {@code jwt_key} to the RS256 JWKS) is the entire migration.
 */
public interface TokenMinter {

    /** @return a signed JWT with claims {@code jid}, {@code device_id}, {@code exp}. */
    String mint(String jid, String deviceId);
}
