package com.telcobright.party.v2.registration.api.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.telcobright.party.v2.registration.internal.RegistrationDenied;
import com.telcobright.party.v2.registration.internal.RegistrationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Frozen §2 registration endpoints (Quarkus root /api/v1):
 *
 *   POST /api/v1/registration/otp/start  { phone }                     -> 202 { otpToken }
 *   POST /api/v1/registration/otp/verify { otpToken, code, device_id } -> 200 { jid, xmppCredential,
 *                                            refreshToken, displayName, domain, host, port }
 *
 * The OTP code NEVER rides a response — dev mode logs it server-side.
 */
@Path("/registration")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class RegistrationResource {

    @Inject RegistrationService service;

    public record OtpStartRequest(String phone) {}

    public record OtpStartResponse(String otpToken) {}

    public record OtpVerifyRequest(String otpToken, String code,
                                   @JsonProperty("device_id") String deviceId) {}

    public record OtpVerifyResponse(String jid, String xmppCredential, String refreshToken,
                                    String displayName, String domain, String host, int port) {}

    @POST
    @Path("/otp/start")
    public Response startOtp(OtpStartRequest req) {
        if (req == null) throw RegistrationDenied.badRequest("missing body");
        String otpToken = service.startOtp(req.phone());
        return Response.accepted(new OtpStartResponse(otpToken)).build();
    }

    @POST
    @Path("/otp/verify")
    public OtpVerifyResponse verifyOtp(OtpVerifyRequest req) {
        if (req == null) throw RegistrationDenied.badRequest("missing body");
        RegistrationService.VerifiedDevice v =
                service.verifyOtp(req.otpToken(), req.code(), req.deviceId());
        return new OtpVerifyResponse(v.jid(), v.xmppCredential(), v.refreshToken(),
                v.displayName(), v.domain(), v.host(), v.port());
    }
}
