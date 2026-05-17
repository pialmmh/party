package com.telcobright.party.v2.api;

import com.telcobright.party.v2.adapter.Role;
import com.telcobright.party.v2.adapter.UserProfile;
import com.telcobright.party.v2.config.TenantRegistry;
import com.telcobright.party.v2.policy.PolicyChain;
import com.telcobright.party.v2.policy.PolicyContext;
import com.telcobright.party.v2.policy.PolicyOutcome;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * The v2 auth surface. One endpoint for now: validate credentials by running the
 * tenant's policy chain. The chain's first (default) policy is basic-auth, which
 * delegates password verification to the tenant's UserRepoAdapter.
 *
 * Path inside Quarkus root-path /api/v1 → final URL: /api/v1/v2/auth/validate.
 */
@Path("/v2/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthV2Resource {

    @Inject
    TenantRegistry registry;

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
            String reason,
            String policyName,
            UserDto user
    ) {}

    @POST
    @Path("/validate")
    public Response validate(ValidateRequest req) {
        if (req == null) {
            return badRequest("missing body");
        }
        String tenantId = req.tenantId() == null || req.tenantId().isBlank()
                ? registry.defaultTenant()
                : req.tenantId();

        PolicyChain chain;
        try {
            chain = registry.chain(tenantId);
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ValidateResponse(false, ex.getMessage(), null, null))
                    .build();
        }

        PolicyContext ctx = new PolicyContext(tenantId, "authenticate", req.login(), req.password());
        PolicyOutcome out = chain.run(ctx);
        UserDto userDto = ctx.resolvedUser != null ? toDto(ctx.resolvedUser) : null;
        ValidateResponse body = new ValidateResponse(
                !out.rejected(),
                out.reason(),
                out.policyName(),
                userDto);
        return Response.ok(body).build();
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
                .entity(new ValidateResponse(false, msg, null, null))
                .build();
    }
}
