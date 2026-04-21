package com.telcobright.party.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Thin HTTP client that talks to Party's /internal/kc/* endpoints.
 * Shared-secret auth via X-KC-Integration-Secret header.
 */
class PartyClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper json = new ObjectMapper();

    private final String baseUrl;
    private final String secret;

    PartyClient(String baseUrl, String secret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.secret = secret;
    }

    Optional<JsonNode> findByUsername(String realm, String username) {
        String url = baseUrl + "/api/v1/internal/kc/users/by-username?realm=" +
                enc(realm) + "&username=" + enc(username);
        return get(url);
    }

    Optional<JsonNode> findById(String realm, String id) {
        String url = baseUrl + "/api/v1/internal/kc/users/by-id?realm=" + enc(realm) + "&id=" + enc(id);
        return get(url);
    }

    Optional<JsonNode> search(String realm, String query, int first, int max) {
        String url = baseUrl + "/api/v1/internal/kc/users/search?realm=" + enc(realm)
                + "&q=" + enc(query == null ? "" : query)
                + "&first=" + first + "&max=" + max;
        return get(url);
    }

    boolean validateCredentials(String realm, String username, String password) {
        try {
            String body = json.writeValueAsString(Map.of(
                    "realm", realm, "username", username, "password", password));
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/internal/kc/users/validate-credentials"))
                    .header("Content-Type", "application/json")
                    .header("X-KC-Integration-Secret", secret)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return false;
            JsonNode n = json.readTree(resp.body());
            return n.path("valid").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private Optional<JsonNode> get(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("X-KC-Integration-Secret", secret)
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) return Optional.empty();
            if (resp.statusCode() / 100 != 2) return Optional.empty();
            return Optional.of(json.readTree(resp.body()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
