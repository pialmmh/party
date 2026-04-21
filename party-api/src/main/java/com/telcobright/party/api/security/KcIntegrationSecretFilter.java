package com.telcobright.party.api.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;

/**
 * Protects /internal/kc/* endpoints with a shared secret.
 * Header: {@code X-KC-Integration-Secret: <secret>}.
 */
@Provider
public class KcIntegrationSecretFilter implements ContainerRequestFilter {

    public static final String HEADER = "X-KC-Integration-Secret";

    @ConfigProperty(name = "party.kc-integration.secret",
                    defaultValue = "dev-kc-integration-secret-change-me")
    String expected;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String path = ctx.getUriInfo().getPath();
        if (!path.startsWith("internal/kc/") && !path.startsWith("/internal/kc/")) return;

        // Allow the healthz probe without a secret.
        if (path.endsWith("/internal/kc/healthz") || path.endsWith("internal/kc/healthz")) return;

        String got = ctx.getHeaderString(HEADER);
        if (!safeEquals(got, expected)) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
        }
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ab.length != bb.length) return false;
        int result = 0;
        for (int i = 0; i < ab.length; i++) result |= ab[i] ^ bb[i];
        return result == 0;
    }
}
