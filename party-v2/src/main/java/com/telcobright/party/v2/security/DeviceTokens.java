package com.telcobright.party.v2.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Verifies the per-device HS256 JWTs that registration mints (frozen §2) using
 * the shared {@link JwtSharedKey}, so party can authenticate its own tokens on
 * any endpoint (contacts owner identity, future authenticated surfaces).
 * Module-root building block: features must not reach into each other's
 * internals for this.
 */
@ApplicationScoped
public class DeviceTokens {

    public record DeviceClaims(String jid, String deviceId) {}

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    private final ObjectMapper json = new ObjectMapper();

    @Inject JwtSharedKey sharedKey;

    /** Empty on ANY defect: malformed, bad signature, expired, missing claims. */
    public Optional<DeviceClaims> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();
            if (!signatureMatches(parts[0] + "." + parts[1], parts[2])) return Optional.empty();
            JsonNode claims = json.readTree(B64.decode(parts[1]));
            if (claims.path("exp").asLong() < Instant.now().getEpochSecond()) return Optional.empty();
            String jid = claims.path("jid").asText(null);
            String deviceId = claims.path("device_id").asText(null);
            if (jid == null || deviceId == null) return Optional.empty();
            return Optional.of(new DeviceClaims(jid, deviceId));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean signatureMatches(String signingInput, String signature) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(sharedKey.bytes(), "HmacSHA256"));
        byte[] expected = mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        return MessageDigest.isEqual(expected, B64.decode(signature));
    }
}
