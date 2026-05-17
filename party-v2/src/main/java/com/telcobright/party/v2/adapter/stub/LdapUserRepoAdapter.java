package com.telcobright.party.v2.adapter.stub;

import com.telcobright.party.v2.adapter.AuthResult;
import com.telcobright.party.v2.adapter.HealthStatus;
import com.telcobright.party.v2.adapter.UserProfile;
import com.telcobright.party.v2.adapter.UserRepoAdapter;
import com.telcobright.party.v2.adapter.UserRepoType;

import java.util.List;
import java.util.Optional;

public final class LdapUserRepoAdapter implements UserRepoAdapter {

    private final String url;
    private final String baseDn;

    public LdapUserRepoAdapter(String url, String baseDn) {
        this.url = url;
        this.baseDn = baseDn;
    }

    @Override public UserRepoType type() { return UserRepoType.LDAP; }
    @Override public AuthResult authenticate(String login, String password) {
        return AuthResult.deny("ldap adapter not implemented");
    }
    @Override public Optional<UserProfile> findByLogin(String login) { return Optional.empty(); }
    @Override public List<UserProfile> search(String query, int first, int max) { return List.of(); }
    @Override public HealthStatus checkHealth() { return HealthStatus.UNKNOWN; }
    @Override public String describeTarget() {
        if (url == null) return "(unconfigured)";
        return baseDn == null || baseDn.isBlank() ? url : url + " · " + baseDn;
    }
}
