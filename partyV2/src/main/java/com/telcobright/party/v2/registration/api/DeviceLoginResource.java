package com.telcobright.party.v2.registration.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.telcobright.party.v2.registration.internal.RegistrationDenied;
import com.telcobright.party.v2.registration.internal.RegistrationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Prod device-login (frozen §8b, #170 B-direct — ratified). The federated front
 * of the device-JWT: a chat user signs in with phone + credential instead of an
 * SMS code, party validates it against the Odoo facade ({@code check_credentials} —
 * the hash never leaves Odoo), and returns the SAME bundle as OTP-verify.
 *
 * <pre>
 *   POST /api/v1/device/login { phone, password, device_id, tenantId? }
 *     -> 200 { jid, xmppCredential, refreshToken, displayName, domain, host, port }
 *     -> 401 invalid credentials · 403 inactive / no subscription · 503 directory down
 * </pre>
 *
 * Identity keys on the E.164 phone (ratified): {@code res.users.login = E.164},
 * {@code jid = <E.164 digits>@<vhost>}, {@code X-SL-Account = E.164}. The response
 * is byte-identical to {@code /registration/otp/verify} so the apps' post-login
 * wiring is the same regardless of how the device-JWT was obtained.
 */
@Path("/device")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class DeviceLoginResource {

    @Inject RegistrationService service;

    /** {@code phone} is the canonical E.164 field; {@code login} is accepted as an alias. */
    public record LoginRequest(String tenantId, String phone, String login, String password,
                               @JsonProperty("device_id") String deviceId) {}

    public record LoginResponse(String jid, String xmppCredential, String refreshToken,
                                String displayName, String domain, String host, int port) {}

    @POST
    @Path("/login")
    public LoginResponse login(LoginRequest req) {
        if (req == null) throw RegistrationDenied.badRequest("missing body");
        String phone = (req.phone() != null && !req.phone().isBlank()) ? req.phone() : req.login();
        RegistrationService.VerifiedDevice v =
                service.loginDevice(phone, req.password(), req.deviceId());
        return new LoginResponse(v.jid(), v.xmppCredential(), v.refreshToken(),
                v.displayName(), v.domain(), v.host(), v.port());
    }
}
