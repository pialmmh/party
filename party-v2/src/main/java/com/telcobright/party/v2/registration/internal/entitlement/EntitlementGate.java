package com.telcobright.party.v2.registration.internal.entitlement;
import com.telcobright.party.v2.registration.internal.RegistrationConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Entitlement check at ISSUANCE (frozen §2): does the partner hold an ACTIVE
 * im_subscription in the subscription authority? secure-link is an enforcement
 * point, same pattern as the radius/PBX/SMS gateways.
 *
 * {@code entitlement.enforce=false} keeps the gate open (legacy default);
 * when enforced it delegates to {@link EntitlementClient}, which queries the
 * RTC-Manager entitlement endpoint (served today by portal-api over the
 * rtc_mock mirror).
 */
@ApplicationScoped
public class EntitlementGate {

    @Inject RegistrationConfig cfg;
    @Inject EntitlementClient client;

    public boolean hasActiveImSubscription(long partnerId, String e164) {
        if (!cfg.entitlement().enforce()) {
            return true;
        }
        return client.hasActiveImSubscription(partnerId, e164);
    }
}
