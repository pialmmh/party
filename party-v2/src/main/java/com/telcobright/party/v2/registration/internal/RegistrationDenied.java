package com.telcobright.party.v2.registration.internal;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Denials surfaced by the registration pipeline. A WebApplicationException so
 * resources stay thin — Quarkus REST renders the prepared JSON response.
 * Reasons are deliberately generic on the wire (no account-existence oracle);
 * specifics go to the log.
 */
public class RegistrationDenied extends WebApplicationException {

    private RegistrationDenied(int status, String reason) {
        super(Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", reason))
                .build());
    }

    public static RegistrationDenied badRequest(String reason) {
        return new RegistrationDenied(400, reason);
    }

    public static RegistrationDenied unauthorized(String reason) {
        return new RegistrationDenied(401, reason);
    }

    public static RegistrationDenied forbidden(String reason) {
        return new RegistrationDenied(403, reason);
    }

    public static RegistrationDenied notFound(String reason) {
        return new RegistrationDenied(404, reason);
    }

    public static RegistrationDenied unavailable(String reason) {
        return new RegistrationDenied(503, reason);
    }
}
