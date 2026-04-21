package com.telcobright.party.api.resource;

import com.telcobright.party.master.service.AuthenticationService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject AuthenticationService auth;

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    @POST
    @Path("/login")
    public Map<String, Object> login(LoginRequest req) {
        var result = auth.login(req.email(), req.password());
        return Map.of(
                "accessToken", result.accessToken(),
                "refreshToken", result.refreshToken(),
                "scope", result.scope(),
                "userId", result.operatorUserId(),
                "operatorId", result.operatorId() == null ? "" : result.operatorId()
        );
    }
}
