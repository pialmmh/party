package com.telcobright.party.v2.registration.internal.otp;

import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.TestClock;
import com.telcobright.party.v2.testkit.TestRegistrationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Frozen §2 OTP discipline: single-use, TTL-bounded, attempt-limited. */
class OtpChallengeStoreTest {

    private OtpChallengeStore store;
    private TestRegistrationConfig cfg;
    private TestClock clock;

    @BeforeEach
    void setUp() {
        store = new OtpChallengeStore();
        cfg = new TestRegistrationConfig();
        clock = TestClock.at("2026-06-11T10:00:00Z");
        Beans.set(store, "cfg", cfg);
        Beans.set(store, "clock", clock);
    }

    @Test
    void verify_returnsPhone_andIsSingleUse() {
        String token = store.issue("+8801711000001", "123456");
        assertEquals("+8801711000001", store.verifyAndConsume(token, "123456").orElseThrow());
        assertTrue(store.verifyAndConsume(token, "123456").isEmpty(), "challenge is consumed");
    }

    @Test
    void wrongCode_failsButLeavesAttempts_thenRightCodeStillWorks() {
        String token = store.issue("+8801711000001", "123456");
        assertTrue(store.verifyAndConsume(token, "000000").isEmpty());
        assertEquals("+8801711000001", store.verifyAndConsume(token, "123456").orElseThrow());
    }

    @Test
    void maxAttempts_killsTheChallenge_evenForTheRightCode() {
        cfg.otpMaxAttempts = 3;
        String token = store.issue("+8801711000001", "123456");
        for (int i = 0; i < 3; i++) {
            assertTrue(store.verifyAndConsume(token, "000000").isEmpty());
        }
        assertTrue(store.verifyAndConsume(token, "123456").isEmpty(), "burned by attempts");
    }

    @Test
    void expiry_isClockDriven() {
        cfg.otpTtlSeconds = 300;
        String token = store.issue("+8801711000001", "123456");
        clock.advance(Duration.ofSeconds(301));
        assertTrue(store.verifyAndConsume(token, "123456").isEmpty(), "expired");
    }

    @Test
    void newCode_isSixDigits() {
        for (int i = 0; i < 50; i++) {
            assertTrue(store.newCode().matches("\\d{6}"));
        }
    }
}
