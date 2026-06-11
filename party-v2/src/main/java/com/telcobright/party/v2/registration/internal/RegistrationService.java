package com.telcobright.party.v2.registration.internal;

import com.telcobright.party.v2.model.E164;
import com.telcobright.party.v2.model.ProviderException;
import com.telcobright.party.v2.api.spi.FacadeDirectory;
import com.telcobright.party.v2.registration.api.emit.DeviceRevoked;
import com.telcobright.party.v2.registration.api.emit.SubscriberProvisioned;
import com.telcobright.party.v2.registration.api.spi.SessionKiller;
import com.telcobright.party.v2.registration.internal.entitlement.EntitlementGate;
import com.telcobright.party.v2.registration.internal.otp.OtpChallengeStore;
import com.telcobright.party.v2.registration.api.spi.OtpSender;
import com.telcobright.party.v2.registration.internal.registry.DeviceRegistryStore;
import com.telcobright.party.v2.registration.internal.registry.DeviceRegistryStore.DeviceRow;
import com.telcobright.party.v2.registration.internal.token.RefreshTokens;
import com.telcobright.party.v2.registration.api.spi.TokenMinter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The registration pipeline (frozen §2). Each public method is one endpoint's
 * business flow; the named steps below carry the detail.
 */
@ApplicationScoped
public class RegistrationService {

    private static final Logger LOG = Logger.getLogger(RegistrationService.class);

    /** device_id doubles as the XMPP resource — keep it resource-safe. */
    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    public record VerifiedDevice(String jid, String xmppCredential, String refreshToken,
                                 String displayName, String domain, String host, int port) {}

    public record RefreshedTokens(String xmppCredential, String refreshToken) {}

    @Inject RegistrationConfig cfg;
    @Inject Clock clock;
    @Inject OtpChallengeStore otpStore;
    @Inject OtpSender otpSender;
    @Inject FacadeDirectory facades;
    @Inject EntitlementGate entitlement;
    @Inject DeviceRegistryStore devices;
    @Inject TokenMinter minter;
    @Inject SessionKiller sessions;
    @Inject RegistrationEvents events;

    public String startOtp(String phone) {
        String e164 = normalizeOrDeny(phone);
        String code = otpStore.newCode();
        deliverCode(e164, code);
        return otpStore.issue(e164, code);
    }

    public VerifiedDevice verifyOtp(String otpToken, String code, String deviceId) {
        requireResourceSafe(deviceId);
        String e164 = consumeChallenge(otpToken, code);
        FacadeDirectory.Facade facade = provisionActiveFacade(e164);
        requireEntitlement(facade.partnerId(), e164);
        String refreshToken = activateDevice(facade, deviceId);
        String jwt = minter.mint(facade.jid(), deviceId);
        events.emit(new SubscriberProvisioned(facade.partnerId(), facade.e164(), facade.jid(), deviceId));
        return new VerifiedDevice(facade.jid(), jwt, refreshToken, facade.displayName(),
                cfg.xmpp().domain(), cfg.xmpp().host(), cfg.xmpp().port());
    }

    public RefreshedTokens refresh(String refreshToken) {
        DeviceRow row = rowForRefreshToken(refreshToken);
        requireActiveNotStale(row);
        requireEntitlement(row.partnerId(), row.e164());
        String next = rotateRefreshToken(row);
        String jwt = minter.mint(jidOf(row.e164()), row.deviceId());
        return new RefreshedTokens(jwt, next);
    }

    /** Central kill: deactivate (never delete) + kick the live session. Idempotent. */
    public void revokeDevice(String deviceId) {
        DeviceRow row = devices.findByDeviceId(deviceId)
                .orElseThrow(() -> RegistrationDenied.notFound("unknown device"));
        devices.setStatus(deviceId, DeviceRegistryStore.REVOKED);
        sessions.kickSession(E164.digits(row.e164()), cfg.xmpp().domain(), deviceId);
        events.emit(new DeviceRevoked(deviceId, row.e164()));
    }

    public List<DeviceRow> listDevices(String account) {
        return devices.listByE164(normalizeOrDeny(account));
    }

    // ── named steps ───────────────────────────────────────────────────────

    private static String normalizeOrDeny(String phone) {
        try {
            return E164.normalize(phone);
        } catch (IllegalArgumentException e) {
            throw RegistrationDenied.badRequest("not a valid E.164 phone number");
        }
    }

    private static void requireResourceSafe(String deviceId) {
        if (deviceId == null || !DEVICE_ID.matcher(deviceId).matches()) {
            throw RegistrationDenied.badRequest(
                    "device_id must be 8-64 chars of [A-Za-z0-9._-] (it becomes the XMPP resource)");
        }
    }

    private void deliverCode(String e164, String code) {
        try {
            otpSender.send(e164, code);
        } catch (IllegalStateException e) {
            LOG.error("OTP delivery unavailable: " + e.getMessage());
            throw RegistrationDenied.unavailable("OTP delivery unavailable");
        }
    }

    private String consumeChallenge(String otpToken, String code) {
        return otpStore.verifyAndConsume(otpToken, code)
                .orElseThrow(() -> RegistrationDenied.unauthorized("invalid or expired code"));
    }

    private FacadeDirectory.Facade provisionActiveFacade(String e164) {
        FacadeDirectory.Facade facade;
        try {
            facade = facades.provision(e164, null);
        } catch (ProviderException e) {
            LOG.error("odoo facade provisioning failed: " + e.getMessage());
            throw RegistrationDenied.unavailable("account provisioning unavailable");
        }
        if (!"active".equals(facade.status())) {
            throw RegistrationDenied.forbidden("account is not active");
        }
        return facade;
    }

    private void requireEntitlement(long partnerId, String e164) {
        if (!entitlement.hasActiveImSubscription(partnerId, e164)) {
            throw RegistrationDenied.forbidden("no active subscription");
        }
    }

    private String activateDevice(FacadeDirectory.Facade facade, String deviceId) {
        String refreshToken = RefreshTokens.newToken();
        devices.upsertActive(deviceId, facade.partnerId(), facade.e164(), RefreshTokens.hash(refreshToken));
        return refreshToken;
    }

    private DeviceRow rowForRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw RegistrationDenied.badRequest("missing refreshToken");
        }
        return devices.findByRefreshTokenHash(RefreshTokens.hash(refreshToken))
                .orElseThrow(() -> RegistrationDenied.unauthorized("refresh refused"));
    }

    private void requireActiveNotStale(DeviceRow row) {
        if (!DeviceRegistryStore.ACTIVE.equals(row.status())) {
            throw RegistrationDenied.unauthorized("refresh refused");
        }
        Instant cutoff = clock.instant().minus(Duration.ofDays(cfg.device().inactivityExpireDays()));
        if (row.lastSeen() != null && row.lastSeen().isBefore(cutoff)) {
            devices.setStatus(row.deviceId(), DeviceRegistryStore.EXPIRED);
            LOG.infof("device %s expired after inactivity (last seen %s)", row.deviceId(), row.lastSeen());
            throw RegistrationDenied.unauthorized("refresh refused");
        }
    }

    private String rotateRefreshToken(DeviceRow row) {
        String next = RefreshTokens.newToken();
        devices.rotateRefreshToken(row.deviceId(), RefreshTokens.hash(next));
        return next;
    }

    private String jidOf(String e164) {
        return E164.digits(e164) + "@" + cfg.xmpp().domain();
    }
}
