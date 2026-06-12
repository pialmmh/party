package com.telcobright.party.keycloak.spi;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The party authentication port — what the Keycloak storage provider REQUIRES:
 * validate a login against party's policy chain (/v2/auth/validate) and get
 * the verdict + the v2 user profile back. Production impl is the HTTP bridge
 * (internal/HttpPartyClient); tests hand in a fake. Implementations never
 * throw — failures come back as a deny so Keycloak never sees a 500.
 */
public interface PartyClient {

    ValidateResult validate(String realmName, String login, String password);

    /** The validate verdict. {@code user} is null when invalid or not returned. */
    record ValidateResult(boolean valid, String reason, JsonNode user, String tenantId) {

        public static ValidateResult deny(String reason, String tenantId) {
            return new ValidateResult(false, reason, null, tenantId);
        }
    }
}
