package com.telcobright.party.master.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@ApplicationScoped
public class TokenService {

    public static final String CLAIM_SCOPE = "scope";
    public static final String CLAIM_OPERATOR_ID = "operatorId";
    public static final String CLAIM_TENANT_ID = "tenantId";
    public static final String CLAIM_PARTNER_ID = "partnerId";
    public static final String CLAIM_ROLES = "roles";

    @ConfigProperty(name = "party.jwt.secret", defaultValue = "dev-secret-change-me-dev-secret-change-me-32bytes")
    String secret;

    @ConfigProperty(name = "party.jwt.issuer", defaultValue = "party-service")
    String issuer;

    @ConfigProperty(name = "party.jwt.access-ttl-minutes", defaultValue = "15")
    long accessTtlMinutes;

    @ConfigProperty(name = "party.jwt.refresh-ttl-minutes", defaultValue = "1440")
    long refreshTtlMinutes;

    private SecretKey key;

    @PostConstruct
    void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // pad to 32 bytes for HS256 minimum
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issueAccessToken(Long subjectId, String email, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(subjectId))
                .claim("email", email)
                .issuer(issuer)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(accessTtlMinutes, ChronoUnit.MINUTES)));
        if (extraClaims != null) {
            extraClaims.forEach(builder::claim);
        }
        return builder.signWith(key).compact();
    }

    public String issueRefreshToken(Long subjectId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(subjectId))
                .claim("type", "refresh")
                .issuer(issuer)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(refreshTtlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        return jws.getPayload();
    }
}
