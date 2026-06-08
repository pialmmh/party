package com.telcobright.party.v2.registration.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.v2.security.JwtSharedKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HS256 JWT minting with the shared {@link JwtSharedKey} (same key ejabberd's
 * {@code jwt_key} verifies with). Hand-rolled on JDK crypto:
 * header.payload.signature, base64url unpadded — no extra dependency for one
 * fixed algorithm. Key loading/validation is the shared key's job, not ours.
 */
@ApplicationScoped
public class HmacTokenMinter implements TokenMinter {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final ObjectMapper json = new ObjectMapper();

    @Inject RegistrationConfig cfg;
    @Inject JwtSharedKey sharedKey;

    @Override
    public String mint(String jid, String deviceId) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("jid", jid);
        claims.put("device_id", deviceId);
        claims.put("iss", cfg.jwt().issuer());
        claims.put("iat", now);
        claims.put("exp", now + cfg.jwt().ttlSeconds());
        return sign(claims);
    }

    private String sign(Map<String, Object> claims) {
        try {
            String header = B64.encodeToString(
                    "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = B64.encodeToString(json.writeValueAsBytes(claims));
            String signingInput = header + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedKey.bytes(), "HmacSHA256"));
            String sig = B64.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
            return signingInput + "." + sig;
        } catch (Exception e) {
            throw new IllegalStateException("JWT minting failed: " + e.getMessage(), e);
        }
    }
}
