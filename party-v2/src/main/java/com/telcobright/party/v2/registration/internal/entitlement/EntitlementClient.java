package com.telcobright.party.v2.registration.internal.entitlement;
import com.telcobright.party.v2.registration.api.spi.EntitlementCheck;
import com.telcobright.party.v2.registration.internal.RegistrationConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Asks the BSS subscription authority whether a partner holds an ACTIVE IM
 * subscription — the RTC-Manager entitlement endpoint, served today by
 * portal-api over the rtc_mock mirror (a config base-url swap moves it to the
 * real RTC-Manager with no code change). GET
 * {entitlement.base-url}/api/v1/clients/{partnerId}/entitlements/im-subscription
 * → {@code {active, packageName, expireDate, source}}.
 *
 * Fail-closed: an unreachable or non-2xx authority denies (returns false) so
 * an enforced gate never lets an unverified device through.
 */
@ApplicationScoped
public class EntitlementClient implements EntitlementCheck {

    private static final Logger LOG = Logger.getLogger(EntitlementClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject HttpClient http;
    @Inject RegistrationConfig cfg;

    @Override
    public boolean hasActiveImSubscription(long partnerId, String e164) {
        String url = entitlementUrl(partnerId);
        try {
            HttpResponse<String> resp = http.send(getJson(url), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                LOG.warnf("entitlement check %s → http %d: denying %s", url, resp.statusCode(), e164);
                return false;
            }
            return isActive(resp.body(), partnerId, e164);
        } catch (Exception e) {
            LOG.warnf("entitlement check failed for %s (%s) — denying: %s", e164, url, e.getMessage());
            return false;
        }
    }

    private String entitlementUrl(long partnerId) {
        String baseUrl = cfg.entitlement().baseUrl().orElseThrow(
                () -> new IllegalStateException("entitlement.enforce=true without entitlement.base-url"));
        return baseUrl + "/api/v1/clients/" + partnerId + "/entitlements/im-subscription";
    }

    private static HttpRequest getJson(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
    }

    private static boolean isActive(String body, long partnerId, String e164) throws Exception {
        JsonNode json = JSON.readTree(body);
        boolean active = json.path("active").asBoolean(false);
        if (!active) {
            LOG.infof("entitlement: no active IM subscription for %s (partner %d)", e164, partnerId);
        }
        return active;
    }
}
