package com.telcobright.party.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.master.entity.Operator;
import com.telcobright.party.master.entity.Tenant;
import com.telcobright.party.master.kc.RealmDecoder;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Creates a Keycloak realm per tenant and attaches the party-user-storage provider.
 * Enabled only when {@code party.keycloak.admin.enabled=true}; otherwise a no-op bean is used.
 *
 * Called from ProvisionTenantWorkflow as the final step after the tenant DB is up.
 */
@ApplicationScoped
@IfBuildProperty(name = "party.keycloak.admin.enabled", stringValue = "true")
public class KeycloakRealmProvisioner {

    private static final Logger LOG = Logger.getLogger(KeycloakRealmProvisioner.class);

    @ConfigProperty(name = "party.keycloak.admin.url")    String kcUrl;
    @ConfigProperty(name = "party.keycloak.admin.realm")  String adminRealm;
    @ConfigProperty(name = "party.keycloak.admin.client-id") String clientId;
    @ConfigProperty(name = "party.keycloak.admin.username")  String adminUser;
    @ConfigProperty(name = "party.keycloak.admin.password")  String adminPass;

    @ConfigProperty(name = "party.kc-integration.secret") String integrationSecret;
    @ConfigProperty(name = "quarkus.http.port", defaultValue = "18081") int partyPort;
    @ConfigProperty(name = "party.keycloak.party-base-url",
                    defaultValue = "http://party:18081") String partyBaseUrl;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    public void createRealmForTenant(Long tenantId) {
        Tenant t = Tenant.findById(tenantId);
        if (t == null) throw new NotFoundException("tenant " + tenantId + " not found");
        Operator op = Operator.findById(t.operatorId);
        if (op == null) throw new NotFoundException("operator " + t.operatorId + " not found");

        String realmName = RealmDecoder.tenantRealmName(op.shortName, t.shortName);
        try {
            String adminToken = obtainAdminToken();
            if (!realmExists(realmName, adminToken)) {
                createRealm(realmName, adminToken);
            }
            ensureStorageProvider(realmName, adminToken);
            ensureDefaultRoles(realmName, adminToken, List.of("admin", "reseller", "agent", "viewer"));
            LOG.infof("Keycloak realm %s configured for tenant %d", realmName, tenantId);
        } catch (Exception e) {
            throw new RuntimeException("Keycloak realm setup failed for tenant " + tenantId, e);
        }
    }

    private String obtainAdminToken() throws Exception {
        String body = "grant_type=password" +
                "&client_id=" + clientId +
                "&username=" + adminUser +
                "&password=" + adminPass;
        HttpRequest req = HttpRequest.newBuilder(URI.create(
                        kcUrl + "/realms/" + adminRealm + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) throw new RuntimeException("admin token request failed: " + resp.statusCode());
        return json.readTree(resp.body()).path("access_token").asText();
    }

    private boolean realmExists(String realm, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(kcUrl + "/admin/realms/" + realm))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() == 200;
    }

    private void createRealm(String realm, String token) throws Exception {
        String body = json.writeValueAsString(Map.of("realm", realm, "enabled", true));
        HttpRequest req = HttpRequest.newBuilder(URI.create(kcUrl + "/admin/realms"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2 && resp.statusCode() != 409) {
            throw new RuntimeException("createRealm " + realm + " failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    private void ensureStorageProvider(String realm, String token) throws Exception {
        // Check if a party-user-storage component already exists
        HttpRequest list = HttpRequest.newBuilder(URI.create(
                        kcUrl + "/admin/realms/" + realm + "/components?type=org.keycloak.storage.UserStorageProvider"))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> listResp = http.send(list, HttpResponse.BodyHandlers.ofString());
        if (listResp.statusCode() / 100 == 2 && listResp.body().contains("\"providerId\":\"party-user-storage\"")) {
            return;
        }
        String realmId = fetchRealmId(realm, token);
        Map<String, Object> body = Map.of(
                "name", "party-user-storage",
                "providerId", "party-user-storage",
                "providerType", "org.keycloak.storage.UserStorageProvider",
                "parentId", realmId,
                "config", Map.of(
                        "partyBaseUrl", List.of(partyBaseUrl),
                        "partyIntegrationSecret", List.of(integrationSecret)
                ));
        HttpRequest req = HttpRequest.newBuilder(URI.create(
                        kcUrl + "/admin/realms/" + realm + "/components"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("attach storage provider failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    private String fetchRealmId(String realm, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(kcUrl + "/admin/realms/" + realm))
                .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return json.readTree(resp.body()).path("id").asText();
    }

    private void ensureDefaultRoles(String realm, String token, List<String> roleNames) throws Exception {
        for (String name : roleNames) {
            String body = json.writeValueAsString(Map.of("name", name));
            HttpRequest req = HttpRequest.newBuilder(URI.create(
                            kcUrl + "/admin/realms/" + realm + "/roles"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 201 && resp.statusCode() != 409) {
                LOG.warnf("ensureRole %s: %d %s", name, resp.statusCode(), resp.body());
            }
        }
    }
}
