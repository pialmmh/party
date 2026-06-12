package com.telcobright.party.v2.registration.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.telcobright.party.v2.registration.spi.DeviceRegistryStore.DeviceRow;
import com.telcobright.party.v2.registration.internal.RegistrationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Frozen §2 device administration (portal/admin — WhatsApp Linked-devices):
 *
 *   GET  /api/v1/devices?account=<e164>         -> [ { device_id, status, last_seen, push_token? } ]
 *   POST /api/v1/devices/{device_id}/revoke     -> 204    // CENTRAL KILL
 */
@Path("/devices")
@Produces(MediaType.APPLICATION_JSON)
public class DevicesResource {

    @Inject RegistrationService service;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeviceDto(
            @JsonProperty("device_id") String deviceId,
            String status,
            @JsonProperty("last_seen") String lastSeen,
            @JsonProperty("push_token") String pushToken) {}

    @GET
    public List<DeviceDto> list(@QueryParam("account") String account) {
        return service.listDevices(account).stream().map(DevicesResource::toDto).toList();
    }

    @POST
    @Path("/{device_id}/revoke")
    public Response revoke(@PathParam("device_id") String deviceId) {
        service.revokeDevice(deviceId);
        return Response.noContent().build();
    }

    private static DeviceDto toDto(DeviceRow row) {
        return new DeviceDto(row.deviceId(), row.status(),
                row.lastSeen() == null ? null : row.lastSeen().toString(),
                row.pushToken());
    }
}
