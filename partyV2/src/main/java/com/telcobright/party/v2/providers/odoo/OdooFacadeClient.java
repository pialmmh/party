package com.telcobright.party.v2.providers.odoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.telcobright.party.v2.spi.FacadeDirectory;
import com.telcobright.party.v2.config.PartyV2Config;
import com.telcobright.party.v2.model.ProviderException;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Odoo impl of the shared {@link FacadeDirectory} port — gateway to the
 * secure_link.facade model (the addon stream-b authored in odoo-backend-19)
 * over the existing JSON-RPC client. Runs as the tenant's Odoo admin user —
 * party is a trusted backend.
 *
 * <p>{@code @DefaultBean}: the production directory, used unless a non-default
 * {@link FacadeDirectory} (the dev seed) is enabled for that build.
 */
@ApplicationScoped
@DefaultBean
public class OdooFacadeClient implements FacadeDirectory {

    private static final List<String> SUMMARY_FIELDS = List.of("id", "e164", "jid", "status", "partner_id");

    @Inject PartyV2Config partyCfg;
    @Inject java.net.http.HttpClient http;

    @ConfigProperty(name = "party.v2.registration.tenant", defaultValue = "t1")
    String tenant;

    private volatile OdooJsonRpcClient client;
    private volatile String adminUser;
    private volatile String adminPassword;

    /** Find-or-create partner + facade for a verified phone (idempotent in Odoo). */
    @Override
    public Facade provision(String e164, String displayName) {
        // Odoo treats false as Python None; List.of rejects nulls.
        JsonNode r = executeKw("provision_for_e164",
                List.of(e164, displayName == null ? Boolean.FALSE : displayName), Map.of());
        if (r == null || !r.isObject()) {
            throw new ProviderException("unexpected facade response: " + r);
        }
        return new Facade(
                r.path("facade_id").asLong(),
                r.path("partner_id").asLong(),
                r.path("e164").asText(),
                r.path("jid").asText(),
                r.path("status").asText(),
                r.path("display_name").asText(null));
    }

    /** Read-only lookup — does this phone have a facade (i.e. is it on the app)? */
    @Override
    public Optional<Facade> findByE164(String e164) {
        List<Facade> rows = searchByE164In(List.of(e164));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Batch read for contact sync: which of these numbers have facades. */
    @Override
    public List<Facade> searchByE164In(List<String> e164s) {
        if (e164s.isEmpty()) return List.of();
        JsonNode rows = executeKw("search_read",
                List.of(List.of(List.of("e164", "in", e164s))),
                Map.of("fields", SUMMARY_FIELDS));
        List<Facade> out = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode r : rows) out.add(toSearchedFacade(r));
        }
        return out;
    }

    // ── internals ─────────────────────────────────────────────────────────

    private JsonNode executeKw(String method, List<?> args, Map<String, ?> kwargs) {
        OdooJsonRpcClient c = clientOrInit();
        int uid = c.authenticate(adminUser, adminPassword)
                .orElseThrow(() -> new ProviderException("odoo admin authenticate failed"));
        return c.executeKw(uid, adminPassword, "secure_link.facade", method, args, kwargs);
    }

    /** search_read row: partner_id arrives as the Odoo m2o pair [id, display_name]. */
    private static Facade toSearchedFacade(JsonNode r) {
        JsonNode partner = r.path("partner_id");
        long partnerId = partner.isArray() ? partner.path(0).asLong() : partner.asLong();
        String displayName = partner.isArray() ? partner.path(1).asText(null) : null;
        return new Facade(
                r.path("id").asLong(),
                partnerId,
                r.path("e164").asText(),
                r.path("jid").asText(),
                r.path("status").asText(),
                displayName);
    }

    private OdooJsonRpcClient clientOrInit() {
        OdooJsonRpcClient c = client;
        if (c == null) {
            PartyV2Config.TenantConfig tc = partyCfg.tenants().get(tenant);
            if (tc == null) throw new ProviderException("registration tenant not configured: " + tenant);
            PartyV2Config.OdooAdapterConfig oc = tc.odoo().orElseThrow(
                    () -> new ProviderException("registration tenant has no odoo config: " + tenant));
            adminUser = oc.adminUser().orElseThrow(
                    () -> new ProviderException("odoo admin-user required for facade provisioning"));
            adminPassword = oc.adminPassword().orElseThrow(
                    () -> new ProviderException("odoo admin-password required for facade provisioning"));
            c = new OdooJsonRpcClient(http, oc.baseUrl(), oc.db(), oc.timeoutMillis());
            client = c;
        }
        return c;
    }
}
