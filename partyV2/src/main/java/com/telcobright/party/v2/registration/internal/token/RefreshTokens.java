package com.telcobright.party.v2.registration.internal.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh-token helpers. The token itself goes only to the device; the
 * registry stores its SHA-256 hex hash (frozen §2: refresh_token_hash).
 */
public final class RefreshTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokens() {}

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
