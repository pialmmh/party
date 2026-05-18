package com.telcobright.party.v2.api.health;

import com.telcobright.party.v2.config.TenantRegistry;
import com.telcobright.party.v2.model.HealthStatus;
import com.telcobright.party.v2.providers.UserRepoProvider;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only view of which providers are configured per tenant and whether
 * they answer a cheap health probe.
 *
 * Final URL: /api/v1/v2/health/adapters
 * (Path kept as /adapters for backwards compatibility with the UI; the DTO
 *  is now provider-flavored.)
 */
@Path("/v2/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @Inject
    TenantRegistry registry;

    public record ProviderHealthDto(
            String tenantId,
            String type,
            String status,
            String target,
            String error
    ) {}

    @GET
    @Path("/adapters")
    public List<ProviderHealthDto> providers() {
        List<ProviderHealthDto> out = new ArrayList<>();
        for (String t : registry.tenantIds()) {
            try {
                UserRepoProvider p = registry.provider(t);
                HealthStatus h = p.checkHealth();
                out.add(new ProviderHealthDto(
                        t,
                        p.type().name().toLowerCase(),
                        h.name().toLowerCase(),
                        p.describeTarget(),
                        null));
            } catch (Exception e) {
                out.add(new ProviderHealthDto(
                        t,
                        "unknown",
                        "bad",
                        null,
                        e.getMessage()));
            }
        }
        return out;
    }
}
