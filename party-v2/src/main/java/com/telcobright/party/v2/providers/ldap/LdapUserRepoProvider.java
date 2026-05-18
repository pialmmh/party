package com.telcobright.party.v2.providers.ldap;

import com.telcobright.party.v2.model.AuthResult;
import com.telcobright.party.v2.model.HealthStatus;
import com.telcobright.party.v2.model.UserProfile;
import com.telcobright.party.v2.providers.UserRepoProvider;
import com.telcobright.party.v2.providers.UserRepoType;

import java.util.List;
import java.util.Optional;

public final class LdapUserRepoProvider implements UserRepoProvider {

    private final String url;
    private final String baseDn;

    public LdapUserRepoProvider(String url, String baseDn) {
        this.url = url;
        this.baseDn = baseDn;
    }

    @Override public UserRepoType type() { return UserRepoType.LDAP; }
    @Override public AuthResult authenticate(String login, String password) {
        return AuthResult.deny("ldap provider not implemented");
    }
    @Override public Optional<UserProfile> findByLogin(String login) { return Optional.empty(); }
    @Override public List<UserProfile> search(String query, int first, int max) { return List.of(); }
    @Override public HealthStatus checkHealth() { return HealthStatus.UNKNOWN; }
    @Override public String describeTarget() {
        if (url == null) return "(unconfigured)";
        return baseDn == null || baseDn.isBlank() ? url : url + " · " + baseDn;
    }
}
