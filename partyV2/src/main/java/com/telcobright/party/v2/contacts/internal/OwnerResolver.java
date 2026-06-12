package com.telcobright.party.v2.contacts.internal;

import com.telcobright.party.v2.model.E164;
import com.telcobright.party.v2.security.DeviceTokens;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves the owner identity for contact endpoints (frozen §6): the §2
 * device JWT's {@code jid} claim when presented; the {@code X-SL-Account}
 * header only in dev mode. NEVER a query param.
 */
@ApplicationScoped
public class OwnerResolver {

    @Inject DeviceTokens tokens;
    @Inject ContactsConfig cfg;

    /** @return the owner's E.164 ({@code +<digits>}). */
    public String resolve(String authorizationHeader, String devAccountHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return ownerFromToken(authorizationHeader.substring("Bearer ".length()).trim());
        }
        if (cfg.ownerHeaderEnabled() && devAccountHeader != null && !devAccountHeader.isBlank()) {
            return normalize(devAccountHeader);
        }
        throw Denied.unauthorized("missing credentials");
    }

    private String ownerFromToken(String token) {
        DeviceTokens.DeviceClaims claims = tokens.verify(token)
                .orElseThrow(() -> Denied.unauthorized("invalid token"));
        String local = claims.jid().split("@", 2)[0];
        return normalize("+" + local);
    }

    private static String normalize(String phone) {
        try {
            return E164.normalize(phone);
        } catch (IllegalArgumentException e) {
            throw Denied.unauthorized("invalid account identity");
        }
    }
}
