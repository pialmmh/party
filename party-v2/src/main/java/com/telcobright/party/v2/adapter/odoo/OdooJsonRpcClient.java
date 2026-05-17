package com.telcobright.party.v2.adapter.odoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.v2.adapter.AdapterException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal JSON-RPC client for Odoo's external API.
 *
 * Two services are used:
 *   - "common"   for version / authenticate
 *   - "object"   for execute_kw against any model
 *
 * The wire shape is fixed: POST {baseUrl}/jsonrpc with a body like
 *   { jsonrpc: "2.0", method: "call",
 *     params: { service, method, args: [...] },
 *     id: <correlator> }
 */
public final class OdooJsonRpcClient {

    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String endpoint;
    private final String db;
    private final Duration timeout;
    private final AtomicLong corr = new AtomicLong(1);

    public OdooJsonRpcClient(String baseUrl, String db, int timeoutMillis) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("odoo baseUrl required");
        }
        if (db == null || db.isBlank()) {
            throw new IllegalArgumentException("odoo db required");
        }
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.endpoint = trimmed + "/jsonrpc";
        this.db = db;
        this.timeout = Duration.ofMillis(timeoutMillis <= 0 ? 5000 : timeoutMillis);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public String db() { return db; }
    public String endpoint() { return endpoint; }

    /**
     * common.version. Returns null on failure; never throws.
     * Used by health check.
     */
    public JsonNode version() {
        try {
            return rpc("common", "version", List.of()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * common.authenticate(db, login, password, {}). Returns uid on success, empty otherwise.
     */
    public Optional<Integer> authenticate(String login, String password) {
        return rpc("common", "authenticate", List.of(db, login, password, Map.of()))
                .filter(JsonNode::isNumber)
                .map(JsonNode::asInt)
                .filter(uid -> uid > 0);
    }

    /**
     * object.execute_kw(db, uid, password, model, method, args, kwargs).
     */
    public JsonNode executeKw(int uid, String password, String model, String method,
                              List<?> args, Map<String, ?> kwargs) {
        List<Object> rpcArgs = List.of(db, uid, password, model, method, args,
                kwargs == null ? Map.of() : kwargs);
        return rpc("object", "execute_kw", rpcArgs).orElseThrow(
                () -> new AdapterException("odoo execute_kw returned null: " + model + "." + method));
    }

    // ── internals ─────────────────────────────────────────────────────────

    private Optional<JsonNode> rpc(String service, String method, List<?> args) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("method", "call");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("service", service);
        params.put("method", method);
        params.put("args", args);
        body.put("params", params);
        body.put("id", corr.getAndIncrement());

        try {
            byte[] payload = json.writeValueAsBytes(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() / 100 != 2) {
                throw new AdapterException(
                        "odoo http " + resp.statusCode() + " from " + service + "." + method);
            }
            JsonNode root = json.readTree(resp.body());
            if (root.has("error") && !root.get("error").isNull()) {
                String msg = root.get("error").has("message")
                        ? root.get("error").get("message").asText()
                        : root.get("error").toString();
                throw new AdapterException("odoo error: " + msg);
            }
            JsonNode result = root.get("result");
            return result == null ? Optional.empty() : Optional.of(result);
        } catch (AdapterException ae) {
            throw ae;
        } catch (Exception e) {
            throw new AdapterException("odoo rpc failed: " + service + "." + method + " — " + e.getMessage(), e);
        }
    }

    /**
     * Build a Jackson-friendly map from an array of (key, value) pairs.
     * Used by callers building kwargs.
     */
    public static Map<String, Object> kwargs(Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("kwargs requires even args");
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return m;
    }
}
