package com.telcobright.party.v2.security;

import com.telcobright.party.v2.registration.internal.token.HmacTokenMinter;
import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.TestClock;
import com.telcobright.party.v2.testkit.TestRegistrationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Frozen §2 token seam: what the minter signs, the verifier accepts — and nothing else. */
class JwtRoundTripTest {

    @TempDir Path dir;

    private JwtSharedKey keyFor(String secret) throws Exception {
        Path f = dir.resolve("jwt.key");
        Files.writeString(f, secret);
        JwtSharedKey key = new JwtSharedKey();
        Beans.set(key, "secretFile", Optional.of(f.toString()));
        return key;
    }

    private HmacTokenMinter minter(JwtSharedKey key, TestRegistrationConfig cfg, TestClock clock) {
        HmacTokenMinter m = new HmacTokenMinter();
        Beans.set(m, "cfg", cfg);
        Beans.set(m, "sharedKey", key);
        Beans.set(m, "clock", clock);
        return m;
    }

    private DeviceTokens verifier(JwtSharedKey key, TestClock clock) {
        DeviceTokens v = new DeviceTokens();
        Beans.set(v, "sharedKey", key);
        Beans.set(v, "clock", clock);
        return v;
    }

    @Test
    void mintThenVerify_roundTripsClaims() throws Exception {
        JwtSharedKey key = keyFor("0123456789abcdef0123456789abcdef");
        TestClock clock = TestClock.at("2026-06-11T10:00:00Z");
        String jwt = minter(key, new TestRegistrationConfig(), clock)
                .mint("8801711000001@localhost", "device-A-0001");

        DeviceTokens.DeviceClaims claims = verifier(key, clock).verify(jwt).orElseThrow();
        assertEquals("8801711000001@localhost", claims.jid());
        assertEquals("device-A-0001", claims.deviceId());
    }

    @Test
    void tamperedSignature_isRejected() throws Exception {
        JwtSharedKey key = keyFor("0123456789abcdef0123456789abcdef");
        TestClock clock = TestClock.at("2026-06-11T10:00:00Z");
        String jwt = minter(key, new TestRegistrationConfig(), clock).mint("u@localhost", "device-A-0001");
        String bad = jwt.substring(0, jwt.length() - 2) + (jwt.endsWith("AA") ? "BB" : "AA");
        assertTrue(verifier(key, clock).verify(bad).isEmpty());
    }

    @Test
    void expiredToken_isRejected_byClock() throws Exception {
        JwtSharedKey key = keyFor("0123456789abcdef0123456789abcdef");
        TestRegistrationConfig cfg = new TestRegistrationConfig();
        cfg.jwtTtlSeconds = 900;
        TestClock clock = TestClock.at("2026-06-11T10:00:00Z");
        String jwt = minter(key, cfg, clock).mint("u@localhost", "device-A-0001");

        DeviceTokens v = verifier(key, clock);
        assertTrue(v.verify(jwt).isPresent(), "fresh token verifies");
        clock.advance(Duration.ofSeconds(901));
        assertTrue(v.verify(jwt).isEmpty(), "expired token refused");
    }

    @Test
    void shortKey_failsFast_onFirstUse() throws Exception {
        JwtSharedKey key = keyFor("too-short");
        TestClock clock = TestClock.at("2026-06-11T10:00:00Z");
        HmacTokenMinter m = minter(key, new TestRegistrationConfig(), clock);
        assertThrows(IllegalStateException.class, () -> m.mint("u@localhost", "device-A-0001"));
    }
}
