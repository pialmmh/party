package com.telcobright.party.v2.policy;

import com.telcobright.party.v2.model.UserProfile;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable per-request context flowing through an {@link ApiChain}.
 *
 * Upstream policies write into the context (e.g. {@link com.telcobright.party.v2.providers.odoo.OdooLoginPolicy}
 * attaches the resolved user) so downstream policies can read it.
 */
public class AuthContext {

    public String tenantId;
    public String endpoint;            // e.g. "basicLogin"
    public String action;              // e.g. "authenticate"

    public String login;
    public String password;            // present only for credential flows

    public String remoteIp;
    public Instant requestedAt = Instant.now();

    public UserProfile user;           // set by login policy on success

    public final Map<String, Object> attrs = new HashMap<>();
}
