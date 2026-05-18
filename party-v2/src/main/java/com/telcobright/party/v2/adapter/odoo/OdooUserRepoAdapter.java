package com.telcobright.party.v2.adapter.odoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.telcobright.party.v2.adapter.AdapterException;
import com.telcobright.party.v2.adapter.AuthResult;
import com.telcobright.party.v2.adapter.EntityMeta;
import com.telcobright.party.v2.adapter.FieldMeta;
import com.telcobright.party.v2.adapter.HealthStatus;
import com.telcobright.party.v2.adapter.Role;
import com.telcobright.party.v2.adapter.UserProfile;
import com.telcobright.party.v2.adapter.UserRepoAdapter;
import com.telcobright.party.v2.adapter.UserRepoType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Odoo-backed user repository.
 *
 * Authentication uses {@code common.authenticate}; the user's own password is then re-used
 * for {@code execute_kw} read calls (Odoo allows res.users self-read with the user's own
 * credentials, so no admin secret is required for basic auth).
 *
 * For administrative read paths (findByLogin / search) AND for schema introspection
 * (the {@link #entities()} call), an admin password is required — configured via
 * {@code odoo.admin-user} + {@code odoo.admin-password}. When those are absent,
 * administrative methods degrade to empty / names-only results.
 */
public final class OdooUserRepoAdapter implements UserRepoAdapter {

    private final OdooJsonRpcClient client;
    private final String baseUrlForDisplay;
    private final String adminUser;
    private final String adminPassword;
    private final List<String> entityNames;

    /** Lazy double-checked cache for the vocabulary. Built on first call to entities(). */
    private volatile List<EntityMeta> entityCache;

    public OdooUserRepoAdapter(String baseUrl, String db, int timeoutMillis,
                                String adminUser, String adminPassword,
                                List<String> entityNames) {
        this.client = new OdooJsonRpcClient(baseUrl, db, timeoutMillis);
        this.baseUrlForDisplay = baseUrl;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        this.entityNames = entityNames == null ? List.of() : List.copyOf(entityNames);
    }

    @Override public UserRepoType type() { return UserRepoType.ODOO; }

    @Override
    public AuthResult authenticate(String login, String password) {
        if (login == null || login.isBlank() || password == null || password.isEmpty()) {
            return AuthResult.deny("missing login or password");
        }
        Optional<Integer> uid;
        try {
            uid = client.authenticate(login, password);
        } catch (AdapterException ae) {
            return AuthResult.deny("adapter error: " + ae.getMessage());
        }
        if (uid.isEmpty()) {
            return AuthResult.deny("invalid credentials");
        }
        try {
            UserProfile profile = readProfile(uid.get(), password);
            return AuthResult.ok(profile);
        } catch (AdapterException ae) {
            return AuthResult.deny("profile read failed: " + ae.getMessage());
        }
    }

    @Override
    public Optional<UserProfile> findByLogin(String login) {
        int admUid = adminUidOrZero();
        if (admUid <= 0) return Optional.empty();
        try {
            JsonNode rows = client.executeKw(admUid, adminPassword,
                    "res.users", "search_read",
                    List.of(List.of(List.of("login", "=", login))),
                    OdooJsonRpcClient.kwargs(
                            "fields", List.of("id", "login", "name", "email", "active", "groups_id"),
                            "limit", 1));
            if (rows == null || !rows.isArray() || rows.isEmpty()) return Optional.empty();
            return Optional.of(toProfile(rows.get(0), admUid));
        } catch (AdapterException ae) {
            throw ae;
        }
    }

    @Override
    public List<UserProfile> search(String query, int first, int max) {
        int admUid = adminUidOrZero();
        if (admUid <= 0) return List.of();
        Object domain = (query == null || query.isBlank())
                ? List.of()
                : List.of(List.of("login", "ilike", query));
        JsonNode rows = client.executeKw(admUid, adminPassword,
                "res.users", "search_read",
                List.of(domain),
                OdooJsonRpcClient.kwargs(
                        "fields", List.of("id", "login", "name", "email", "active", "groups_id"),
                        "offset", Math.max(0, first),
                        "limit", Math.max(1, max)));
        List<UserProfile> out = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) out.add(toProfile(row, admUid));
        }
        return out;
    }

    @Override
    public HealthStatus checkHealth() {
        try {
            JsonNode v = client.version();
            return v != null ? HealthStatus.GOOD : HealthStatus.BAD;
        } catch (Exception e) {
            return HealthStatus.BAD;
        }
    }

    @Override
    public String describeTarget() {
        return baseUrlForDisplay + " · db=" + client.db();
    }

    @Override
    public List<EntityMeta> entities() {
        List<EntityMeta> cached = entityCache;
        if (cached != null) return cached;
        synchronized (this) {
            if (entityCache == null) {
                entityCache = introspect();
            }
            return entityCache;
        }
    }

    // ── introspection ─────────────────────────────────────────────────────

    private List<EntityMeta> introspect() {
        if (entityNames.isEmpty()) return List.of();
        int admUid = adminUidOrZero();
        List<EntityMeta> out = new ArrayList<>();
        for (String model : entityNames) {
            if (model == null || model.isBlank()) continue;
            if (admUid <= 0) {
                // No admin creds → expose names-only so the UI can still render the entities pane.
                out.add(new EntityMeta(model, "odoo", List.of()));
                continue;
            }
            try {
                JsonNode rows = client.executeKw(admUid, adminPassword,
                        "ir.model.fields", "search_read",
                        List.of(List.of(List.of("model", "=", model))),
                        OdooJsonRpcClient.kwargs(
                                "fields", List.of("name", "ttype", "required", "relation", "field_description")));
                List<FieldMeta> fields = new ArrayList<>();
                if (rows != null && rows.isArray()) {
                    for (JsonNode r : rows) {
                        fields.add(new FieldMeta(
                                textOrNull(r, "name"),
                                textOrNull(r, "ttype"),
                                r.has("required") && r.get("required").asBoolean(),
                                textOrNull(r, "relation"),
                                textOrNull(r, "field_description")
                        ));
                    }
                }
                out.add(new EntityMeta(model, "odoo", fields));
            } catch (Exception e) {
                // Introspection failure is non-fatal — record the model name with no fields.
                out.add(new EntityMeta(model, "odoo", List.of()));
            }
        }
        return out;
    }

    // ── internals ─────────────────────────────────────────────────────────

    private UserProfile readProfile(int uid, String password) {
        JsonNode rows = client.executeKw(uid, password,
                "res.users", "read",
                List.of(List.of(uid)),
                OdooJsonRpcClient.kwargs(
                        "fields", List.of("id", "login", "name", "email", "active", "groups_id")));
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            throw new AdapterException("odoo res.users.read returned empty for uid " + uid);
        }
        JsonNode row = rows.get(0);
        List<Role> roles = readRoles(uid, password, row.get("groups_id"));
        return new UserProfile(
                String.valueOf(row.get("id").asInt()),
                textOrNull(row, "login"),
                textOrNull(row, "email"),
                textOrNull(row, "name"),
                row.has("active") && row.get("active").asBoolean(),
                roles,
                Map.of());
    }

    private UserProfile toProfile(JsonNode row, int callerUid) {
        List<Role> roles = readRoles(callerUid, adminPassword, row.get("groups_id"));
        return new UserProfile(
                String.valueOf(row.get("id").asInt()),
                textOrNull(row, "login"),
                textOrNull(row, "email"),
                textOrNull(row, "name"),
                row.has("active") && row.get("active").asBoolean(),
                roles,
                Map.of());
    }

    private List<Role> readRoles(int callerUid, String password, JsonNode groupIdsNode) {
        if (groupIdsNode == null || !groupIdsNode.isArray() || groupIdsNode.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        for (JsonNode n : groupIdsNode) ids.add(n.asInt());
        try {
            JsonNode rows = client.executeKw(callerUid, password,
                    "res.groups", "read",
                    List.of(ids),
                    OdooJsonRpcClient.kwargs("fields", List.of("id", "full_name")));
            List<Role> out = new ArrayList<>();
            if (rows != null && rows.isArray()) {
                for (JsonNode r : rows) {
                    String name = r.has("full_name") ? r.get("full_name").asText() : null;
                    if (name != null && !name.isBlank()) out.add(Role.of(name, "odoo"));
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private int adminUidOrZero() {
        if (adminUser == null || adminUser.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            return 0;
        }
        try {
            return client.authenticate(adminUser, adminPassword).orElse(0);
        } catch (AdapterException e) {
            return 0;
        }
    }

    private static String textOrNull(JsonNode row, String field) {
        if (!row.has(field) || row.get(field).isNull()) return null;
        JsonNode v = row.get(field);
        return v.isBoolean() ? null : v.asText();
    }
}
