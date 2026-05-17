package com.telcobright.party.v2.adapter.stub;

import com.telcobright.party.v2.adapter.AuthResult;
import com.telcobright.party.v2.adapter.HealthStatus;
import com.telcobright.party.v2.adapter.UserProfile;
import com.telcobright.party.v2.adapter.UserRepoAdapter;
import com.telcobright.party.v2.adapter.UserRepoType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pluggable hook. A real custom adapter loads via the className supplied in config
 * (Class.forName + reflection); this scaffold accepts the config and returns
 * "not implemented" until that loader lands.
 */
public final class CustomUserRepoAdapter implements UserRepoAdapter {

    private final String className;
    private final Map<String, String> properties;

    public CustomUserRepoAdapter(String className, Map<String, String> properties) {
        this.className = className;
        this.properties = properties == null ? Map.of() : properties;
    }

    @Override public UserRepoType type() { return UserRepoType.CUSTOM; }
    @Override public AuthResult authenticate(String login, String password) {
        return AuthResult.deny("custom adapter not implemented");
    }
    @Override public Optional<UserProfile> findByLogin(String login) { return Optional.empty(); }
    @Override public List<UserProfile> search(String query, int first, int max) { return List.of(); }
    @Override public HealthStatus checkHealth() { return HealthStatus.UNKNOWN; }
    @Override public String describeTarget() {
        return className != null ? className : "(unconfigured)";
    }
}
