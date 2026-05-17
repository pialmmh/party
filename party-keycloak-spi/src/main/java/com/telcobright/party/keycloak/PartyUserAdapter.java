package com.telcobright.party.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.LegacyUserCredentialManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.storage.adapter.AbstractUserAdapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * UserModel adapter for v2.
 *
 * Lifecycle:
 *   1. {@link PartyUserStorageProvider#getUserByUsername} constructs a SHELL adapter —
 *      just the username, no profile data — so Keycloak's iteration of storage providers
 *      finds a hit and continues to credential validation.
 *   2. {@link PartyUserStorageProvider#isValid} POSTs to /v2/auth/validate. On success it
 *      calls {@link #attachProfile(JsonNode)} on the same adapter instance, so the
 *      issued token has email / displayName / roles available.
 *   3. Subsequent reads on this adapter (during the same login round-trip) return the
 *      attached profile. After Keycloak finishes the round-trip, federation cache /
 *      session storage takes over per the configured federation mode.
 */
class PartyUserAdapter extends AbstractUserAdapter {

    private final String username;
    private volatile JsonNode profile;   // null until isValid attaches it

    PartyUserAdapter(KeycloakSession session, RealmModel realm, ComponentModel storageModel,
                     String username) {
        super(session, realm, storageModel);
        this.username = username;
    }

    /** Called by the storage provider once /v2/auth/validate has returned a user record. */
    void attachProfile(JsonNode userPayload) {
        this.profile = userPayload;
    }

    boolean hasProfile() { return profile != null; }

    /** External id from Odoo / etc — only meaningful when profile is attached. */
    String externalId() {
        return profile == null ? null : textOrNull(profile, "externalId");
    }

    // ── UserModel ─────────────────────────────────────────────────────────

    @Override
    public String getUsername() { return username; }

    @Override
    public String getEmail() {
        return profile == null ? null : textOrNull(profile, "email");
    }

    @Override
    public boolean isEmailVerified() { return true; }

    @Override
    public boolean isEnabled() {
        return profile == null || profile.path("active").asBoolean(true);
    }

    @Override
    public String getFirstName() {
        String dn = profile == null ? null : textOrNull(profile, "displayName");
        if (dn == null || dn.isBlank()) return null;
        int sp = dn.indexOf(' ');
        return sp < 0 ? dn : dn.substring(0, sp);
    }

    @Override
    public String getLastName() {
        String dn = profile == null ? null : textOrNull(profile, "displayName");
        if (dn == null || dn.isBlank()) return null;
        int sp = dn.indexOf(' ');
        return sp < 0 ? null : dn.substring(sp + 1);
    }

    @Override
    public List<String> getAttribute(String name) {
        if (profile == null) return List.of();
        if ("roles".equals(name)) return rolesAsList();
        if ("tenantId".equals(name)) {
            // tenantId is owned by the provider, not the user payload; the provider
            // will set it via attachAttribute if needed. Default empty.
            return List.of();
        }
        return List.of();
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        out.put("username", List.of(username));
        if (getEmail() != null) out.put("email", List.of(getEmail()));
        List<String> rs = rolesAsList();
        if (!rs.isEmpty()) out.put("roles", rs);
        return out;
    }

    @Override
    public Set<RoleModel> getRealmRoleMappings() {
        if (profile == null) return Set.of();
        JsonNode rolesNode = profile.path("roles");
        if (!rolesNode.isArray()) return Set.of();
        Set<RoleModel> out = new LinkedHashSet<>();
        rolesNode.forEach(r -> {
            String name = r.asText(null);
            if (name == null || name.isBlank()) return;
            RoleModel rm = realm.getRole(name);
            if (rm != null) out.add(rm);
        });
        return out;
    }

    @Override
    public SubjectCredentialManager credentialManager() {
        return new LegacyUserCredentialManager(session, realm, this);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private List<String> rolesAsList() {
        if (profile == null) return List.of();
        JsonNode rolesNode = profile.path("roles");
        if (!rolesNode.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        rolesNode.forEach(r -> {
            String n = r.asText(null);
            if (n != null && !n.isBlank()) out.add(n);
        });
        return out;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        JsonNode v = node.get(field);
        return v.isBoolean() ? null : v.asText();
    }
}
