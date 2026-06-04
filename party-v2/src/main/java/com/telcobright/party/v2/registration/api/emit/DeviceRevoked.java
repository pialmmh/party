package com.telcobright.party.v2.registration.api.emit;

/**
 * Emitted on central kill: the device's registry row is deactivated, its live
 * XMPP session kicked, and future token refresh refused. (frozen §2 api/emit)
 */
public record DeviceRevoked(String deviceId, String e164) {}
