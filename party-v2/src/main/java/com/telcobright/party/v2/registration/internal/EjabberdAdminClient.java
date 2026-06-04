package com.telcobright.party.v2.registration.internal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Kicks a live XMPP session on revocation (frozen §2 central kill): ejabberd
 * mod_http_api {@code kick_session} addressed by resource = device_id. The
 * revoked registry row already guarantees refresh refusal; this ends the
 * CURRENT session immediately.
 *
 * Behind {@code ejabberd.kick-enabled} until mod_http_api is enabled on the
 * box (the ejabberd-JWT config task).
 */
@ApplicationScoped
public class EjabberdAdminClient {

    private static final Logger LOG = Logger.getLogger(EjabberdAdminClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    @Inject RegistrationConfig cfg;

    /** Best-effort: revocation must not fail because the kick did. */
    public void kickSession(String user, String host, String resource) {
        if (!cfg.ejabberd().kickEnabled()) {
            LOG.debugf("ejabberd kick disabled — skipping kick of %s@%s/%s", user, host, resource);
            return;
        }
        String apiUrl = cfg.ejabberd().apiUrl().orElseThrow(
                () -> new IllegalStateException("ejabberd.kick-enabled without ejabberd.api-url"));
        String body = String.format(
                "{\"user\":\"%s\",\"host\":\"%s\",\"resource\":\"%s\",\"reason\":\"device revoked\"}",
                user, host, resource);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl + "/api/kick_session"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                LOG.warnf("ejabberd kick_session %s@%s/%s -> http %d: %s",
                        user, host, resource, resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            LOG.warnf("ejabberd kick_session %s@%s/%s failed: %s", user, host, resource, e.getMessage());
        }
    }
}
