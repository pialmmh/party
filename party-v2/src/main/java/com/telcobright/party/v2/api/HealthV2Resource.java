package com.telcobright.party.v2.api;

import com.telcobright.party.v2.adapter.HealthStatus;
import com.telcobright.party.v2.adapter.UserRepoAdapter;
import com.telcobright.party.v2.config.TenantRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only view of which adapters are configured per tenant and whether
 * they answer a cheap health probe.
 *
 * Final URL: /api/v1/v2/health/adapters
 */
@Path("/v2/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthV2Resource {

    @Inject
    TenantRegistry registry;

    public record AdapterHealthDto(
            String tenantId,
            String type,
            String status,
            String target,
            String error
    ) {}

    @GET
    @Path("/adapters")
    public List<AdapterHealthDto> adapters() {
        List<AdapterHealthDto> out = new ArrayList<>();
        for (String t : registry.tenantIds()) {
            try {
                UserRepoAdapter a = registry.adapter(t);
                HealthStatus h = a.checkHealth();
                out.add(new AdapterHealthDto(
                        t,
                        a.type().name().toLowerCase(),
                        h.name().toLowerCase(),
                        a.describeTarget(),
                        null));
            } catch (Exception e) {
                out.add(new AdapterHealthDto(
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
