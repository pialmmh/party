package com.telcobright.party.v2.providers.odoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.telcobright.party.v2.config.PartyV2Config;
import com.telcobright.party.v2.model.ProviderException;
import com.telcobright.party.v2.model.Role;
import com.telcobright.party.v2.model.UserProfile;
import com.telcobright.party.v2.policy.AuthContext;
import com.telcobright.party.v2.policy.AuthPolicy;
import com.telcobright.party.v2.policy.EvalResult;
import com.telcobright.party.v2.providers.UserRepoType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Self-contained Odoo authentication policy. Performs the full login flow
 * against Odoo's external JSON-RPC API — no provider indirection. One policy
 * class is the integration point: the Odoo client cache, the auth call, the
 * profile/role read, and the verdict all live here.
 *
 * <pre>
 *   match condition  : credentials present AND tenant.user-repo-type == ODOO
 *   verdict
 *     allow          : odoo common.authenticate returns a uid AND res.users.read
 *                      succeeds AND user.active = true; ctx.user is attached
 *     deny 1002      : invalid credentials
 *     deny 1003      : odoo transport / wire error
 *     deny 1004      : profile read failed
 *     deny 1101      : account inactive
 *     noMatch        : creds absent, or tenant is not an Odoo tenant
 *                      (the chain moves on so a sibling LdapLoginPolicy /
 *                       RoutesphereLoginPolicy / etc. can take over)
 * </pre>
 *
 * Per-tenant Odoo client cache. Each tenant declares its Odoo backend under
 * {@code party.v2.tenants.<tenantId>.odoo.{base-url,db,timeout-millis}} —
 * different tenants can point at different Odoo deployments without code
 * changes.
 *
 * Password use: the user's own password is reused for the post-auth read
 * calls (Odoo allows self-read on res.users with the user's credentials), so
 * this policy needs no admin secret to do basic login.
 */
@ApplicationScoped
public class OdooLoginPolicy implements AuthPolicy {

    @Inject PartyV2Config cfg;

    private final Map<String, OdooJsonRpcClient> clientByTenant = new ConcurrentHashMap<>();

    @Override public String name() { return "odooLogin"; }

    @Override
    public EvalResult execute(AuthContext ctx) {
        if (ctx.login == null || ctx.login.isBlank()
                || ctx.password == null || ctx.password.isEmpty()) {
            return EvalResult.noMatch();
        }
        OdooSettings settings = settingsFor(ctx.tenantId);
        if (settings == null) {
            return EvalResult.noMatch();
        }
        OdooJsonRpcClient client = clientFor(ctx.tenantId, settings);

        Optional<Integer> uid;
        try {
            uid = client.authenticate(ctx.login, ctx.password);
        } catch (ProviderException ae) {
            return stamp(EvalResult.deny(1003, "odoo error: " + ae.getMessage()));
        }
        if (uid.isEmpty()) {
            return stamp(EvalResult.deny(1002, "invalid credentials"));
        }

        UserProfile profile;
        try {
            profile = readProfile(client, uid.get(), ctx.password);
        } catch (ProviderException ae) {
            return stamp(EvalResult.deny(1004, "profile read failed: " + ae.getMessage()));
        }
        ctx.user = profile;

        if (!profile.active()) {
            return stamp(EvalResult.deny(1101, "account inactive"));
        }

        return stamp(EvalResult.allow());
    }

    private EvalResult stamp(EvalResult r) {
        r.policyName = name();
        return r;
    }

    private OdooSettings settingsFor(String tenantId) {
        if (tenantId == null) return null;
        PartyV2Config.TenantConfig tc = cfg.tenants().get(tenantId);
        if (tc == null || tc.userRepoType() != UserRepoType.ODOO) return null;
        return tc.odoo()
                .map(o -> new OdooSettings(o.baseUrl(), o.db(), o.timeoutMillis()))
                .orElse(null);
    }

    private OdooJsonRpcClient clientFor(String tenantId, OdooSettings s) {
        return clientByTenant.computeIfAbsent(tenantId,
                t -> new OdooJsonRpcClient(s.baseUrl(), s.db(), s.timeoutMillis()));
    }

    private UserProfile readProfile(OdooJsonRpcClient client, int uid, String password) {
        JsonNode rows = client.executeKw(uid, password,
                "res.users", "read",
                List.of(List.of(uid)),
                OdooJsonRpcClient.kwargs(
                        // Odoo 19 renamed res.users.groups_id → group_ids (direct groups).
                        "fields", List.of("id", "login", "name", "email", "active", "group_ids")));
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            throw new ProviderException("odoo res.users.read returned empty for uid " + uid);
        }
        JsonNode row = rows.get(0);
        List<Role> roles = readRoles(client, uid, password, row.get("group_ids"));
        return new UserProfile(
                String.valueOf(row.get("id").asInt()),
                textOrNull(row, "login"),
                textOrNull(row, "email"),
                textOrNull(row, "name"),
                row.has("active") && row.get("active").asBoolean(),
                roles,
                Map.of());
    }

    private List<Role> readRoles(OdooJsonRpcClient client, int uid, String password,
                                 JsonNode groupIdsNode) {
        if (groupIdsNode == null || !groupIdsNode.isArray() || groupIdsNode.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = new ArrayList<>();
        for (JsonNode n : groupIdsNode) ids.add(n.asInt());
        try {
            JsonNode rows = client.executeKw(uid, password,
                    "res.groups", "read",
                    List.of(ids),
                    OdooJsonRpcClient.kwargs("fields", List.of("id", "full_name")));
            List<Role> out = new ArrayList<>();
            if (rows != null && rows.isArray()) {
                for (JsonNode r : rows) {
                    String n = r.has("full_name") ? r.get("full_name").asText() : null;
                    if (n != null && !n.isBlank()) out.add(Role.of(n, "odoo"));
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String textOrNull(JsonNode row, String field) {
        if (!row.has(field) || row.get(field).isNull()) return null;
        JsonNode v = row.get(field);
        return v.isBoolean() ? null : v.asText();
    }

    private record OdooSettings(String baseUrl, String db, int timeoutMillis) {}
}
