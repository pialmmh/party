package com.telcobright.party.v2.registration.internal;

import com.telcobright.party.v2.registration.publishes.DeviceRevoked;
import com.telcobright.party.v2.registration.publishes.SubscriberProvisioned;
import com.telcobright.party.v2.registration.internal.entitlement.EntitlementGate;
import com.telcobright.party.v2.registration.internal.otp.OtpChallengeStore;
import com.telcobright.party.v2.registration.spi.DeviceRegistryStore;
import com.telcobright.party.v2.registration.spi.DeviceRegistryStore.DeviceRow;
import com.telcobright.party.v2.registration.internal.token.RefreshTokens;
import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.FakeFacadeDirectory;
import com.telcobright.party.v2.testkit.TestClock;
import com.telcobright.party.v2.testkit.TestRegistrationConfig;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The §2 flows end-to-end in-unit: every collaborator is a fake behind its port. */
class RegistrationServiceTest {

    /** In-memory impl of the DeviceRegistryStore port. */
    static class FakeDeviceRegistry implements DeviceRegistryStore {
        final Map<String, DeviceRow> rows = new LinkedHashMap<>();

        void seed(DeviceRow row) { rows.put(row.deviceId(), row); }

        @Override public void upsertActive(String deviceId, long partnerId, String e164, String hash) {
            rows.put(deviceId, new DeviceRow(deviceId, partnerId, e164, ACTIVE, hash, null,
                    Instant.parse("2026-06-11T10:00:00Z")));
        }
        @Override public Optional<DeviceRow> findByRefreshTokenHash(String hash) {
            return rows.values().stream().filter(r -> r.refreshTokenHash().equals(hash)).findFirst();
        }
        @Override public Optional<DeviceRow> findByDeviceId(String deviceId) {
            return Optional.ofNullable(rows.get(deviceId));
        }
        @Override public List<DeviceRow> listByE164(String e164) {
            return rows.values().stream().filter(r -> r.e164().equals(e164)).toList();
        }
        @Override public void rotateRefreshToken(String deviceId, String newHash) {
            DeviceRow r = rows.get(deviceId);
            rows.put(deviceId, new DeviceRow(r.deviceId(), r.partnerId(), r.e164(), r.status(),
                    newHash, r.pushToken(), r.lastSeen()));
        }
        @Override public void setStatus(String deviceId, String status) {
            DeviceRow r = rows.get(deviceId);
            rows.put(deviceId, new DeviceRow(r.deviceId(), r.partnerId(), r.e164(), status,
                    r.refreshTokenHash(), r.pushToken(), r.lastSeen()));
        }
    }

    /** Records emits; never touches the CDI event bus. */
    static class FakeEvents extends RegistrationEvents {
        final List<SubscriberProvisioned> provisioned = new ArrayList<>();
        final List<DeviceRevoked> revoked = new ArrayList<>();
        @Override public void emit(SubscriberProvisioned ev) { provisioned.add(ev); }
        @Override public void emit(DeviceRevoked ev) { revoked.add(ev); }
    }

    record Kick(String user, String host, String resource) {}

    private RegistrationService service;
    private TestRegistrationConfig cfg;
    private TestClock clock;
    private OtpChallengeStore otpStore;
    private FakeFacadeDirectory facades;
    private FakeDeviceRegistry devices;
    private FakeEvents events;
    private List<Kick> kicks;
    private boolean entitled;

    @BeforeEach
    void setUp() {
        cfg = new TestRegistrationConfig();
        clock = TestClock.at("2026-06-11T10:00:00Z");
        otpStore = new OtpChallengeStore();
        Beans.set(otpStore, "cfg", cfg);
        Beans.set(otpStore, "clock", clock);
        facades = new FakeFacadeDirectory();
        devices = new FakeDeviceRegistry();
        events = new FakeEvents();
        kicks = new ArrayList<>();
        entitled = true;

        EntitlementGate gate = new EntitlementGate();
        Beans.set(gate, "cfg", cfg);
        Beans.set(gate, "check",
                (com.telcobright.party.v2.registration.spi.EntitlementCheck)
                        (partnerId, e164) -> entitled);

        service = new RegistrationService();
        Beans.set(service, "cfg", cfg);
        Beans.set(service, "clock", clock);
        Beans.set(service, "otpStore", otpStore);
        Beans.set(service, "otpSender",
                (com.telcobright.party.v2.registration.spi.OtpSender) (phone, code) -> {});
        Beans.set(service, "facades", facades);
        Beans.set(service, "entitlement", gate);
        Beans.set(service, "devices", devices);
        Beans.set(service, "minter",
                (com.telcobright.party.v2.registration.spi.TokenMinter)
                        (jid, deviceId) -> "jwt:" + jid + ":" + deviceId);
        Beans.set(service, "sessions",
                (com.telcobright.party.v2.registration.spi.SessionKiller)
                        (user, host, resource) -> kicks.add(new Kick(user, host, resource)));
        Beans.set(service, "events", events);
    }

    private static int status(WebApplicationException e) { return e.getResponse().getStatus(); }

    // ── verifyOtp ────────────────────────────────────────────────────────

    @Test
    void verifyOtp_provisionsActivatesAndMints() {
        String token = otpStore.issue("+8801711000001", "123456");
        RegistrationService.VerifiedDevice v = service.verifyOtp(token, "123456", "device-A-0001");

        assertEquals("8801711000001@localhost", v.jid());
        assertEquals("jwt:8801711000001@localhost:device-A-0001", v.xmppCredential());
        assertNotNull(v.refreshToken());

        DeviceRow row = devices.rows.get("device-A-0001");
        assertEquals(DeviceRegistryStore.ACTIVE, row.status());
        assertEquals(RefreshTokens.hash(v.refreshToken()), row.refreshTokenHash());
        assertEquals(1, events.provisioned.size());
        assertEquals("device-A-0001", events.provisioned.get(0).deviceId());
    }

    @Test
    void verifyOtp_rejectsResourceUnsafeDeviceId_with400() {
        String token = otpStore.issue("+8801711000001", "123456");
        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> service.verifyOtp(token, "123456", "bad id!"));
        assertEquals(400, status(e));
    }

    @Test
    void verifyOtp_deniesWithoutEntitlement_with403() {
        cfg.entitlementEnforce = true;
        entitled = false;
        String token = otpStore.issue("+8801711000001", "123456");
        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> service.verifyOtp(token, "123456", "device-A-0001"));
        assertEquals(403, status(e));
        assertTrue(devices.rows.isEmpty(), "no device activated on denial");
    }

    @Test
    void verifyOtp_deniesSuspendedAccount_with403() {
        facades.seed("+8801711000001", "suspended", "Suspended User");
        String token = otpStore.issue("+8801711000001", "123456");
        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> service.verifyOtp(token, "123456", "device-A-0001"));
        assertEquals(403, status(e));
    }

    @Test
    void verifyOtp_rejectsWrongOrReplayedChallenge_with401() {
        String token = otpStore.issue("+8801711000001", "123456");
        service.verifyOtp(token, "123456", "device-A-0001");
        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> service.verifyOtp(token, "123456", "device-B-0002"));
        assertEquals(401, status(e), "challenge is single-use");
    }

    // ── refresh ──────────────────────────────────────────────────────────

    @Test
    void refresh_rotatesBothTokens() {
        String token = otpStore.issue("+8801711000001", "123456");
        String firstRefresh = service.verifyOtp(token, "123456", "device-A-0001").refreshToken();

        RegistrationService.RefreshedTokens next = service.refresh(firstRefresh);
        assertNotNull(next.xmppCredential());
        assertNotEquals(firstRefresh, next.refreshToken());
        assertTrue(devices.findByRefreshTokenHash(RefreshTokens.hash(firstRefresh)).isEmpty(),
                "old refresh token is dead");
        assertTrue(devices.findByRefreshTokenHash(RefreshTokens.hash(next.refreshToken())).isPresent());
    }

    @Test
    void refresh_isRefused_forRevokedDevice() {
        String token = otpStore.issue("+8801711000001", "123456");
        String refresh = service.verifyOtp(token, "123456", "device-A-0001").refreshToken();
        devices.setStatus("device-A-0001", DeviceRegistryStore.REVOKED);

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> service.refresh(refresh));
        assertEquals(401, status(e));
    }

    @Test
    void refresh_expiresInactiveDevice_andRefuses() {
        cfg.inactivityExpireDays = 30;
        String refresh = "long-lived-refresh-token";
        devices.seed(new DeviceRow("device-A-0001", 101, "+8801711000001",
                DeviceRegistryStore.ACTIVE, RefreshTokens.hash(refresh), null,
                Instant.parse("2026-06-11T10:00:00Z").minus(Duration.ofDays(31))));

        WebApplicationException e = assertThrows(WebApplicationException.class,
                () -> service.refresh(refresh));
        assertEquals(401, status(e));
        assertEquals(DeviceRegistryStore.EXPIRED, devices.rows.get("device-A-0001").status());
    }

    @Test
    void refresh_rejectsUnknownToken_with401_andBlank_with400() {
        assertEquals(401, status(assertThrows(WebApplicationException.class,
                () -> service.refresh("never-issued"))));
        assertEquals(400, status(assertThrows(WebApplicationException.class,
                () -> service.refresh(" "))));
    }

    // ── central kill ─────────────────────────────────────────────────────

    @Test
    void revokeDevice_deactivatesKicksAndEmits() {
        String token = otpStore.issue("+8801711000001", "123456");
        service.verifyOtp(token, "123456", "device-A-0001");

        service.revokeDevice("device-A-0001");

        assertEquals(DeviceRegistryStore.REVOKED, devices.rows.get("device-A-0001").status());
        assertEquals(List.of(new Kick("8801711000001", "localhost", "device-A-0001")), kicks);
        assertEquals(1, events.revoked.size());
    }

    @Test
    void revokeUnknownDevice_is404() {
        assertEquals(404, status(assertThrows(WebApplicationException.class,
                () -> service.revokeDevice("device-Z-9999"))));
    }
}
