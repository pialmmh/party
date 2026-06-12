package com.telcobright.party.v2.providers.routesphere;

import com.telcobright.party.v2.model.AuthResult;
import com.telcobright.party.v2.model.HealthStatus;
import com.telcobright.party.v2.model.UserProfile;
import com.telcobright.party.v2.providers.UserRepoProvider;
import com.telcobright.party.v2.providers.UserRepoType;

import java.util.List;
import java.util.Optional;

/**
 * Stub. To be implemented when the Routesphere-as-user-repo path is needed.
 * Exists so the provider-selection switch is exhaustive and the UI can render
 * a placeholder card for the type.
 */
public final class RoutesphereUserRepoProvider implements UserRepoProvider {

    private final String baseUrl;

    public RoutesphereUserRepoProvider(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override public UserRepoType type() { return UserRepoType.ROUTESPHERE; }
    @Override public AuthResult authenticate(String login, String password) {
        return AuthResult.deny("routesphere provider not implemented");
    }
    @Override public Optional<UserProfile> findByLogin(String login) { return Optional.empty(); }
    @Override public List<UserProfile> search(String query, int first, int max) { return List.of(); }
    @Override public HealthStatus checkHealth() { return HealthStatus.UNKNOWN; }
    @Override public String describeTarget() { return baseUrl != null ? baseUrl : "(unconfigured)"; }
}
