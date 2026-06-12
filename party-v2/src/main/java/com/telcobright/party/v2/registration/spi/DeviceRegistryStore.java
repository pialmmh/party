package com.telcobright.party.v2.registration.spi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The durable device registry port (frozen §2) — one row per (device, account)
 * install. Deactivate-don't-delete: revocation flips status; rows are never
 * removed (Conversations pattern). Production impl is the MySQL adapter
 * (internal/registry); tests hand in an in-memory fake.
 */
public interface DeviceRegistryStore {

    record DeviceRow(String deviceId, long partnerId, String e164, String status,
                     String refreshTokenHash, String pushToken, Instant lastSeen) {}

    String ACTIVE = "ACTIVE";
    String REVOKED = "REVOKED";
    String EXPIRED = "EXPIRED";

    /** Registration (re)claims the device id: same install re-registering goes ACTIVE again. */
    void upsertActive(String deviceId, long partnerId, String e164, String refreshTokenHash);

    Optional<DeviceRow> findByRefreshTokenHash(String hash);

    Optional<DeviceRow> findByDeviceId(String deviceId);

    /** Most recently seen first. */
    List<DeviceRow> listByE164(String e164);

    void rotateRefreshToken(String deviceId, String newHash);

    void setStatus(String deviceId, String status);
}
