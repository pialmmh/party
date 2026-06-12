package com.telcobright.party.v2.registration.api;

import com.telcobright.party.v2.registration.internal.RegistrationDenied;
import com.telcobright.party.v2.registration.internal.RegistrationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Frozen §2 silent re-auth:
 *
 *   POST /api/v1/token/refresh { refreshToken } -> 200 { xmppCredential, refreshToken }
 *
 * Rotates BOTH tokens. Refusal (revoked / expired / suspended) is a plain
 * 401 — the app drops to logged-out and re-runs OTP.
 */
@Path("/token")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TokenResource {

    @Inject RegistrationService service;

    public record RefreshRequest(String refreshToken) {}

    public record RefreshResponse(String xmppCredential, String refreshToken) {}

    @POST
    @Path("/refresh")
    public RefreshResponse refresh(RefreshRequest req) {
        if (req == null) throw RegistrationDenied.badRequest("missing body");
        RegistrationService.RefreshedTokens t = service.refresh(req.refreshToken());
        return new RefreshResponse(t.xmppCredential(), t.refreshToken());
    }
}
