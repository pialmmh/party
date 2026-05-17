package com.telcobright.party.v2.adapter;

import java.util.List;
import java.util.Map;

public record UserProfile(
        String externalId,
        String login,
        String email,
        String displayName,
        boolean active,
        List<Role> roles,
        Map<String, String> attributes
) {
}
