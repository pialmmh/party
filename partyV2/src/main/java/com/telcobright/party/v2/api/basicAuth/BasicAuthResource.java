package com.telcobright.party.v2.api.basicAuth;

import com.telcobright.party.v2.config.TenantRegistry;
import com.telcobright.party.v2.model.Role;
import com.telcobright.party.v2.model.UserProfile;
import com.telcobright.party.v2.policy.ApiChain;
import com.telcobright.party.v2.policy.AuthContext;
import com.telcobright.party.v2.policy.EvalResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * v2 auth surface. Runs the policy chain declared at {@code party.v2.api.basicAuth}
 * (Cisco-ACL semantics) against an {@link AuthContext} carrying the supplied
 * credentials. Policies are self-contained — Odoo / LDAP / Routesphere connectors
 * live inside their own policy classes, not behind a shared provider.
 *
 * Final URL (Quarkus root /api/v1): /api/v1/v2/auth/validate.
 */
@Path("/v2/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BasicAuthResource {

    private static final String ENDPOINT = "basicAuth";

    @Inject TenantRegistry registry;
    @Inject ApiChain       apiChain;

    public record ValidateRequest(String tenantId, String login, String password) {}

    public record UserDto(
            String externalId,
            String login,
            String email,
            String displayName,
            boolean active,
            List<String> roles
    ) {}

    public record ValidateResponse(
            boolean valid,
            int denialCode,
            String reason,
            String ruleName,
            String policyName,
            UserDto user
    ) {}

    @POST
    @Path("/validate")
    public Response validate(ValidateRequest req, @Context HttpHeaders headers) {
        if (req == null) return badRequest("missing body");

        String tenantId = (req.tenantId() == null || req.tenantId().isBlank())
                ? registry.defaultTenant() : req.tenantId();

        if (!registry.tenantIds().contains(tenantId)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ValidateResponse(false, 404,
                            "unknown tenant: " + tenantId, null, null, null))
                    .build();
        }

        AuthContext ctx = new AuthContext();
        ctx.tenantId = tenantId;
        ctx.endpoint = ENDPOINT;
        ctx.action   = "authenticate";
        ctx.login    = req.login();
        ctx.password = req.password();
        ctx.remoteIp = firstHeader(headers, "X-Forwarded-For");

        EvalResult r = apiChain.run(ENDPOINT, ctx);

        UserDto user = ctx.user != null ? toDto(ctx.user) : null;
        return Response.ok(new ValidateResponse(
                r.allow,
                r.denialCode,
                r.denialDescription,
                r.ruleName,
                r.policyName,
                user
        )).build();
    }

    private static String firstHeader(HttpHeaders headers, String name) {
        if (headers == null) return null;
        List<String> vals = headers.getRequestHeader(name);
        return (vals == null || vals.isEmpty()) ? null : vals.get(0);
    }

    private static UserDto toDto(UserProfile p) {
        return new UserDto(
                p.externalId(),
                p.login(),
                p.email(),
                p.displayName(),
                p.active(),
                p.roles().stream().map(Role::name).toList());
    }

    private static Response badRequest(String msg) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ValidateResponse(false, 400, msg, null, null, null))
                .build();
    }
}
