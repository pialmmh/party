package com.telcobright.party.v2.testkit;

import com.telcobright.party.v2.registration.internal.RegistrationConfig;

import java.util.Optional;

/** Hand-rolled RegistrationConfig — the knobs tests turn are mutable fields. */
public final class TestRegistrationConfig implements RegistrationConfig {

    public int otpTtlSeconds = 300;
    public int otpMaxAttempts = 3;
    public Optional<String> jwtSecretFile = Optional.empty();
    public int jwtTtlSeconds = 900;
    public boolean entitlementEnforce = false;
    public boolean kickEnabled = false;
    public Optional<String> ejabberdApiUrl = Optional.empty();
    public int inactivityExpireDays = 30;

    @Override public String tenant() { return "t1"; }

    @Override public Otp otp() {
        return new Otp() {
            @Override public boolean devMode() { return true; }
            @Override public int ttlSeconds() { return otpTtlSeconds; }
            @Override public int maxAttempts() { return otpMaxAttempts; }
        };
    }

    @Override public Jwt jwt() {
        return new Jwt() {
            @Override public Optional<String> secret() { return Optional.empty(); }
            @Override public Optional<String> secretFile() { return jwtSecretFile; }
            @Override public int ttlSeconds() { return jwtTtlSeconds; }
            @Override public String issuer() { return "party"; }
        };
    }

    @Override public Xmpp xmpp() {
        return new Xmpp() {
            @Override public String domain() { return "localhost"; }
            @Override public String host() { return "10.10.185.1"; }
            @Override public int port() { return 5222; }
        };
    }

    @Override public Entitlement entitlement() {
        return new Entitlement() {
            @Override public boolean enforce() { return entitlementEnforce; }
            @Override public Optional<String> baseUrl() { return Optional.of("http://127.0.0.1:7120"); }
        };
    }

    @Override public Ejabberd ejabberd() {
        return new Ejabberd() {
            @Override public boolean kickEnabled() { return kickEnabled; }
            @Override public Optional<String> apiUrl() { return ejabberdApiUrl; }
        };
    }

    @Override public Device device() {
        return () -> inactivityExpireDays;
    }
}
