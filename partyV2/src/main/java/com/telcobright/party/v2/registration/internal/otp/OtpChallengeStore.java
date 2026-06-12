package com.telcobright.party.v2.registration.internal.otp;
import com.telcobright.party.v2.registration.internal.RegistrationConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transient in-memory OTP challenges (frozen §2: party DB keeps only
 * device_registry durable; otp_challenge is transient). Single-use,
 * TTL-bounded, attempt-limited.
 */
@ApplicationScoped
public class OtpChallengeStore {

    public record Challenge(String phone, String code, long expiresAtMs, AtomicInteger attempts) {}

    private final Map<String, Challenge> byToken = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Inject RegistrationConfig cfg;
    @Inject Clock clock;

    public String issue(String phone, String code) {
        purgeExpired();
        String otpToken = UUID.randomUUID().toString();
        long expiry = clock.millis() + cfg.otp().ttlSeconds() * 1000L;
        byToken.put(otpToken, new Challenge(phone, code, expiry, new AtomicInteger()));
        return otpToken;
    }

    public String newCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }

    /** Returns the phone on success and consumes the challenge; empty otherwise. */
    public Optional<String> verifyAndConsume(String otpToken, String code) {
        Challenge ch = byToken.get(otpToken);
        if (ch == null || clock.millis() > ch.expiresAtMs()) {
            byToken.remove(otpToken);
            return Optional.empty();
        }
        if (ch.attempts().incrementAndGet() > cfg.otp().maxAttempts()) {
            byToken.remove(otpToken);
            return Optional.empty();
        }
        if (!ch.code().equals(code)) {
            return Optional.empty();
        }
        byToken.remove(otpToken);
        return Optional.of(ch.phone());
    }

    private void purgeExpired() {
        long now = clock.millis();
        byToken.entrySet().removeIf(e -> now > e.getValue().expiresAtMs());
    }
}
