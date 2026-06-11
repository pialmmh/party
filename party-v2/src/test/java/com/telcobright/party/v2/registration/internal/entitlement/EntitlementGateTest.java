package com.telcobright.party.v2.registration.internal.entitlement;

import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.TestRegistrationConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The gate: enforce=false stays open without consulting the authority; enforced delegates. */
class EntitlementGateTest {

    private EntitlementGate gate(boolean enforce, boolean answer, AtomicInteger calls) {
        TestRegistrationConfig cfg = new TestRegistrationConfig();
        cfg.entitlementEnforce = enforce;
        EntitlementGate g = new EntitlementGate();
        Beans.set(g, "cfg", cfg);
        Beans.set(g, "check", (com.telcobright.party.v2.registration.api.spi.EntitlementCheck)
                (partnerId, e164) -> { calls.incrementAndGet(); return answer; });
        return g;
    }

    @Test
    void enforceOff_isOpen_andNeverCallsTheAuthority() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(gate(false, false, calls).hasActiveImSubscription(1, "+880171"));
        assertEquals(0, calls.get());
    }

    @Test
    void enforceOn_delegatesToThePort() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(gate(true, true, calls).hasActiveImSubscription(1, "+880171"));
        assertFalse(gate(true, false, calls).hasActiveImSubscription(1, "+880171"));
        assertEquals(2, calls.get());
    }
}
