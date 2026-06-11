package com.telcobright.party.keycloak.internal;

import com.sun.net.httpserver.HttpServer;
import com.telcobright.party.keycloak.api.spi.PartyClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The HTTP bridge against an embedded server — wire parsing + the never-throws deny contract. */
class HttpPartyClientTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int respondStatus = 200;
    private volatile String respondJson = "{}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/v2/auth/validate", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = respondJson.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(respondStatus, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpPartyClient client(String tenantOverride) {
        return new HttpPartyClient(HttpClient.newHttpClient(), base, tenantOverride);
    }

    @Test
    void validResponse_roundTripsVerdictAndProfile() {
        respondJson = """
                {"valid":true,"reason":null,"policyName":"odooLogin",
                 "user":{"id":"7","login":"alice","email":"a@x"}}""";

        PartyClient.ValidateResult res = client(null).validate("master", "alice", "pw");

        assertTrue(res.valid());
        assertNull(res.reason());
        assertEquals("alice", res.user().path("login").asText());
        assertTrue(lastBody.get().contains("\"login\":\"alice\""));
        assertTrue(lastBody.get().contains("\"password\":\"pw\""));
    }

    @Test
    void invalidResponse_carriesTheReason() {
        respondJson = "{\"valid\":false,\"reason\":\"policy denied\",\"user\":null}";
        PartyClient.ValidateResult res = client(null).validate("master", "alice", "bad");
        assertFalse(res.valid());
        assertEquals("policy denied", res.reason());
        assertNull(res.user());
    }

    @Test
    void non200_isADeny_neverAThrow() {
        respondStatus = 500;
        PartyClient.ValidateResult res = client(null).validate("master", "alice", "pw");
        assertFalse(res.valid());
        assertEquals("http 500", res.reason());
    }

    @Test
    void unreachableParty_isADeny_neverAThrow() {
        server.stop(0); // kill the endpoint before the call
        PartyClient.ValidateResult res = client(null).validate("master", "alice", "pw");
        assertFalse(res.valid());
        assertTrue(res.reason() != null && !res.reason().isBlank());
    }

    @Test
    void tenantResolution_realmConventionAndOverride() {
        respondJson = "{\"valid\":true,\"user\":null}";

        client(null).validate("tenant-btcl-t1", "alice", "pw");
        assertTrue(lastBody.get().contains("\"tenantId\":\"t1\""), "realm convention tenant-<op>-<tn> -> <tn>");

        client(null).validate("master", "alice", "pw");
        assertTrue(lastBody.get().contains("\"tenantId\":\"master\""), "plain realm name used as-is");

        client("forced").validate("tenant-btcl-t1", "alice", "pw");
        assertTrue(lastBody.get().contains("\"tenantId\":\"forced\""), "config override beats derivation");
    }
}
