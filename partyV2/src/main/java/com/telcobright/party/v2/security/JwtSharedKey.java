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
 * Module-root building block. The file is placed on the boxes by the operator
 * (never in git) and read WITHOUT a trailing newline; minimum 32 chars.
 */
@ApplicationScoped
public class JwtSharedKey {

    @ConfigProperty(name = "party.v2.registration.jwt.secret-file")
    Optional<String> secretFile;

    private volatile byte[] cached;

    /** @return the key bytes; fails fast (clear message) if the file is unset/short. */
    public byte[] bytes() {
        byte[] k = cached;
        if (k == null) {
            k = load();
            cached = k;
        }
        return k;
    }

    private byte[] load() {
        String path = secretFile.orElseThrow(() -> new IllegalStateException(
                "party.v2.registration.jwt.secret-file not configured — no HS256 key for mint/verify"));
        String raw;
        try {
            raw = Files.readString(Path.of(path)).strip();
        } catch (Exception e) {
            throw new IllegalStateException("JWT shared key unreadable: " + path, e);
        }
        if (raw.length() < 32) {
            throw new IllegalStateException("JWT shared key too short (< 32 chars): " + path);
        }
        return raw.getBytes(StandardCharsets.UTF_8);
    }
}
