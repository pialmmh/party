package com.telcobright.party.v2.adapter;

import java.util.Optional;

public record AuthResult(boolean valid, Optional<UserProfile> profile, String reason) {

    public static AuthResult ok(UserProfile profile) {
        return new AuthResult(true, Optional.of(profile), null);
    }

    public static AuthResult deny(String reason) {
        return new AuthResult(false, Optional.empty(), reason);
    }
}
