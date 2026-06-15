package com.telcobright.party.v2.security;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The single HS256 shared-key (frozen §2): the byte-identical secret that
 * party mints tokens with and ejabberd's {@code jwt_key} verifies them by.
 * One owner of "load + cache + validate the key" so the minter only signs and
 * the verifier only verifies — neither manages key material.
 *
 * Module-root building block. The key comes from OpenBao (quarkus-vault) where a
 * vault is configured, or an operator-placed file otherwise; never from git.
 * Read WITHOUT a trailing newline; minimum 32 chars.
 */
@ApplicationScoped
public class JwtSharedKey {

    /**
     * The key value itself — PREFERRED. On it_vm this is injected from OpenBao
     * via {@code party.v2.registration.jwt.secret=${sljwt.key}} (quarkus-vault),
     * so the secret never lands in a file or env var.
     */
    @ConfigProperty(name = "party.v2.registration.jwt.secret")
    Optional<String> secretValue = Optional.empty();

    /**
     * Fallback: a file holding the key (operator-placed). Kept for tests and any
     * box without a vault. Used only when {@link #secretValue} is absent/blank.
     */
    @ConfigProperty(name = "party.v2.registration.jwt.secret-file")
    Optional<String> secretFile = Optional.empty();

    private volatile byte[] cached;

    /** @return the key bytes; fails fast (clear message) if neither source yields a valid key. */
    public byte[] bytes() {
        byte[] k = cached;
        if (k == null) {
            k = load();
            cached = k;
        }
        return k;
    }

    private byte[] load() {
        // Prefer the directly-injected value (OpenBao via quarkus-vault); fall back
        // to an operator-placed file. Exactly one must yield a >=32-char key.
        String direct = secretValue.map(String::strip).filter(s -> !s.isEmpty()).orElse(null);
        String raw, source;
        if (direct != null) {
            raw = direct;
            source = "party.v2.registration.jwt.secret";
        } else {
            String path = secretFile.filter(s -> !s.isBlank()).orElseThrow(() -> new IllegalStateException(
                    "no HS256 key: set party.v2.registration.jwt.secret (vault) or party.v2.registration.jwt.secret-file"));
            try {
                raw = Files.readString(Path.of(path)).strip();
            } catch (Exception e) {
                throw new IllegalStateException("JWT shared key unreadable: " + path, e);
            }
            source = path;
        }
        if (raw.length() < 32) {
            throw new IllegalStateException("JWT shared key too short (< 32 chars): " + source);
        }
        return raw.getBytes(StandardCharsets.UTF_8);
    }
}
