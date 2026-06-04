package com.telcobright.party.v2.contacts.internal;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Contacts-feature denials as ready-made JSON responses (the feature's own —
 * internals are not shared across features).
 */
public final class Denied {

    private Denied() {}

    public static WebApplicationException badRequest(String reason) {
        return failure(400, reason);
    }

    public static WebApplicationException unauthorized(String reason) {
        return failure(401, reason);
    }

    /** Stale/invalid sync cursor — the client must refetch the snapshot (frozen §6). */
    public static WebApplicationException gone(String reason) {
        return failure(410, reason);
    }

    public static WebApplicationException unavailable(String reason) {
        return failure(503, reason);
    }

    private static WebApplicationException failure(int status, String reason) {
        return new WebApplicationException(Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", reason))
                .build());
    }
}
