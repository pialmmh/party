package com.telcobright.party.v2.adapter.stub;

import com.telcobright.party.v2.adapter.AuthResult;
import com.telcobright.party.v2.adapter.HealthStatus;
import com.telcobright.party.v2.adapter.UserProfile;
import com.telcobright.party.v2.adapter.UserRepoAdapter;
import com.telcobright.party.v2.adapter.UserRepoType;

import java.util.List;
import java.util.Optional;

/**
 * Stub. To be implemented when the Routesphere-as-user-repo path is needed.
 * Today this exists so the adapter selection switch is exhaustive and so the UI
 * can render a placeholder card for the type.
 */
public final class RoutesphereUserRepoAdapter implements UserRepoAdapter {

    private final String baseUrl;

    public RoutesphereUserRepoAdapter(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override public UserRepoType type() { return UserRepoType.ROUTESPHERE; }
    @Override public AuthResult authenticate(String login, String password) {
        return AuthResult.deny("routesphere adapter not implemented");
    }
    @Override public Optional<UserProfile> findByLogin(String login) { return Optional.empty(); }
    @Override public List<UserProfile> search(String query, int first, int max) { return List.of(); }
    @Override public HealthStatus checkHealth() { return HealthStatus.UNKNOWN; }
    @Override public String describeTarget() { return baseUrl != null ? baseUrl : "(unconfigured)"; }
}
