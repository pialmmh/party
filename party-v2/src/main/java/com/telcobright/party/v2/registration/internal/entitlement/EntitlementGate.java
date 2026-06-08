package com.telcobright.party.v2.registration.internal.entitlement;
import com.telcobright.party.v2.registration.internal.RegistrationConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Entitlement check at ISSUANCE (frozen §2): does the partner hold an ACTIVE
 * im_subscription PackageAccount in RTC-Manager? secure-link is an
 * enforcement point, same pattern as the radius/PBX/SMS gateways.
 *
 * Behind {@code entitlement.enforce=false} until the RTC-Manager endpoint is
 * agreed — the platform's KB-events bridge is a known gap, so end-to-end
 * purchase isn't testable yet. Designed against PackageAccount ACTIVE.
 */
@ApplicationScoped
public class EntitlementGate {

    private static final Logger LOG = Logger.getLogger(EntitlementGate.class);

    @Inject RegistrationConfig cfg;

    public boolean hasActiveImSubscription(long partnerId, String e164) {
        if (!cfg.entitlement().enforce()) {
            return true;
        }
        // TODO wire the agreed RTC-Manager endpoint (PackageAccount ACTIVE for im_subscription).
        LOG.warnf("entitlement.enforce=true but RTC-Manager client not wired — denying %s", e164);
        return false;
    }
}
