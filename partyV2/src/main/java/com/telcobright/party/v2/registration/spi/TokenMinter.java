package com.telcobright.party.v2.registration.spi;

/**
 * Mints the short-TTL per-device access token (the {@code xmppCredential}).
 *
 * Frozen §2: party mints HS256 day one (the HS256 minter); Keycloak
 * arrives LATER as the minter only — swapping this implementation (and
 * ejabberd's {@code jwt_key} to the RS256 JWKS) is the entire migration.
 */
public interface TokenMinter {

    /**
     * @param personId the owner's global person key (the {@code person_id} claim) —
     *                 lets the sync-proxy derive the device's contacts feed.
     * @return a signed JWT with claims {@code jid}, {@code device_id},
     *         {@code person_id}, {@code exp}.
     */
    String mint(String jid, String deviceId, String personId);
}
