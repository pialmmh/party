package com.telcobright.party.api.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.Map;

@Path("/ping")
public class PingResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> ping() {
        return Map.of(
                "service", "party",
                "status", "ok",
                "timestamp", Instant.now().toString()
        );
    }
}
