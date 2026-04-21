package com.telcobright.party.master.service;

import com.telcobright.party.domain.UserStatus;
import com.telcobright.party.master.entity.OperatorUser;
import com.telcobright.party.master.security.PasswordHasher;
import com.telcobright.party.master.security.TokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAuthorizedException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class AuthenticationService {

    @Inject PasswordHasher hasher;
    @Inject TokenService tokens;

    public record LoginResult(String accessToken, String refreshToken, String scope,
                              Long operatorUserId, Long operatorId) {}

    @Transactional
    public LoginResult login(String email, String password) {
        OperatorUser user = OperatorUser.find("email", email).firstResult();
        if (user == null || user.status != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("invalid credentials");
        }
        if (!hasher.verify(password, user.passwordHash)) {
            throw new NotAuthorizedException("invalid credentials");
        }
        user.lastLoginAt = Instant.now();

        Map<String, Object> claims = new HashMap<>();
        claims.put(TokenService.CLAIM_SCOPE, user.role.name());
        if (user.operatorId != null) {
            claims.put(TokenService.CLAIM_OPERATOR_ID, user.operatorId);
        }
        String access = tokens.issueAccessToken(user.id, user.email, claims);
        String refresh = tokens.issueRefreshToken(user.id);
        return new LoginResult(access, refresh, user.role.name(), user.id, user.operatorId);
    }
}
