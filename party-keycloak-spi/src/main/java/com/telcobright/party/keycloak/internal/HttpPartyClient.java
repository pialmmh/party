package com.telcobright.party.keycloak.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.keycloak.api.spi.PartyClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The HTTP impl of the {@link PartyClient} port. Calls one endpoint:
 *   POST {baseUrl}/api/v1/v2/auth/validate
 *
 * Body:  { "tenantId": "...", "login": "...", "password": "..." }
 * Reply: { "valid": bool, "reason": string|null, "policyName": string|null, "user": { ... }|null }
 *
 * Party (v2) is the policy decision point: the chain (odooLogin → nocReadOnly → ...)
 * decides allow/deny under Cisco-ACL semantics. This class is only the
 * Keycloak-side bridge — it knows nothing about Odoo, LDAP, or anything else.
 * The HttpClient is handed in by the factory (the composition root) — never
 * self-built — so tests can fake the wire.
 */
public final class HttpPartyClient implements PartyClient {

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    private final String baseUrl;
    private final String tenantOverride;

    public HttpPartyClient(HttpClient http, String baseUrl, String tenantOverride) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("partyBaseUrl is required");
        }
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.tenantOverride = (tenantOverride == null || tenantOverride.isBlank()) ? null : tenantOverride;
    }

    /** Never throws — failures return a deny so the SPI degrades gracefully. */
    @Override
    public ValidateResult validate(String realmName, String login, String password) {
        String tenantId = resolveTenant(realmName);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tenantId", tenantId);
            body.put("login", login);
            body.put("password", password);

            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/v2/auth/validate"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return ValidateResult.deny("http " + resp.statusCode(), null);
            }
            JsonNode n = json.readTree(resp.body());
            boolean valid = n.path("valid").asBoolean(false);
            String reason = n.has("reason") && !n.get("reason").isNull()
                    ? n.get("reason").asText() : null;
            JsonNode user = n.has("user") && !n.get("user").isNull()
                    ? n.get("user") : null;
            return new ValidateResult(valid, reason, user, tenantId);
        } catch (Exception e) {
            return ValidateResult.deny(e.getClass().getSimpleName() + ": " + e.getMessage(), tenantId);
        }
    }

    /** Resolve which tenantId to send. Config override beats realm-name derivation. */
    private String resolveTenant(String realmName) {
        if (tenantOverride != null) return tenantOverride;
        if (realmName == null) return null;
        // Convention: realm "tenant-<op>-<tn>" maps to tenant "<tn>".  Otherwise use as-is.
        if (realmName.startsWith("tenant-")) {
            int last = realmName.lastIndexOf('-');
            if (last > "tenant-".length() - 1) {
                return realmName.substring(last + 1);
            }
        }
        return realmName;
    }

    public String baseUrl() { return baseUrl; }
    public String tenantOverride() { return tenantOverride; }
}
