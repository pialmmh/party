package com.telcobright.party.master.kc;

import java.util.List;
import java.util.Map;

/**
 * Flattened user record returned to Keycloak's User Storage SPI.
 * Shape is deliberately simple — our SPI adapter converts it to a Keycloak UserModel.
 */
public record KcUserView(
        String id,                     // party's primary key as string ("op-<id>" or "u-<id>")
        String username,               // canonical login identifier (= email)
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean emailVerified,
        Map<String, List<String>> attributes   // operatorId, tenantId, partnerId, scope, roles, permissions
) {}
