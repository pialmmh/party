package com.telcobright.party.v2.registration.spi;

/**
 * Entitlement authority port (frozen §2): does the partner hold an ACTIVE IM
 * subscription? The production impl queries the BSS/RTC-Manager entitlement
 * endpoint (stream-e owns the server side); tests hand in a fake. Consumed by
 * the EntitlementGate behind {@code entitlement.enforce}.
 */
public interface EntitlementCheck {

    boolean hasActiveImSubscription(long partnerId, String e164);
}
