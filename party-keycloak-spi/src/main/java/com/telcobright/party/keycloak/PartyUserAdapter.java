package com.telcobright.party.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.LegacyUserCredentialManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.storage.adapter.AbstractUserAdapter;

import java.util.*;

/**
 * Bridges a {@link com.telcobright.party.master.kc.KcUserView}-shaped JSON from Party
 * into Keycloak's {@link org.keycloak.models.UserModel}.
 */
class PartyUserAdapter extends AbstractUserAdapter {

    private final JsonNode payload;
    private final String partyId;

    PartyUserAdapter(KeycloakSession session, RealmModel realm, ComponentModel storageModel, JsonNode payload) {
        super(session, realm, storageModel);
        this.payload = payload;
        this.partyId = payload.path("id").asText();
    }

    @Override public String getUsername() { return payload.path("username").asText(); }
    @Override public String getEmail()    { return payload.path("email").asText(null); }
    @Override public String getFirstName(){ return payload.path("firstName").asText(null); }
    @Override public String getLastName() { return payload.path("lastName").asText(null); }
    @Override public boolean isEnabled()  { return payload.path("enabled").asBoolean(true); }
    @Override public boolean isEmailVerified() { return payload.path("emailVerified").asBoolean(true); }

    /** External party id; Keycloak wraps this into {@code f:<providerId>:<partyId>} for its own records. */
    String partyId() { return partyId; }

    @Override
    public List<String> getAttribute(String name) {
        JsonNode attrs = payload.path("attributes");
        if (!attrs.isObject()) return List.of();
        JsonNode vals = attrs.path(name);
        if (vals.isMissingNode()) return List.of();
        if (vals.isArray()) {
            List<String> out = new ArrayList<>();
            vals.forEach(v -> out.add(v.asText()));
            return out;
        }
        return List.of(vals.asText());
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        JsonNode attrs = payload.path("attributes");
        if (attrs.isObject()) {
            attrs.fields().forEachRemaining(e -> {
                List<String> vals = new ArrayList<>();
                if (e.getValue().isArray()) {
                    e.getValue().forEach(v -> vals.add(v.asText()));
                } else {
                    vals.add(e.getValue().asText());
                }
                out.put(e.getKey(), vals);
            });
        }
        out.putIfAbsent("username", List.of(getUsername()));
        if (getEmail() != null) out.putIfAbsent("email", List.of(getEmail()));
        return out;
    }

    @Override
    public SubjectCredentialManager credentialManager() {
        // Credential verification is delegated to the CredentialInputValidator
        // on our UserStorageProvider (which calls Party). This manager is required
        // by the UserModel contract but isn't the decision-maker here.
        return new LegacyUserCredentialManager(session, realm, this);
    }
}
