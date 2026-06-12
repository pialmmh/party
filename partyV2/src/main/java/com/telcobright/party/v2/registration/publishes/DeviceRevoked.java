package com.telcobright.party.v2.registration.publishes;

/**
 * Emitted on central kill: the device's registry row is deactivated, its live
 * XMPP session kicked, and future token refresh refused. (frozen §2 publishes/)
 */
public record DeviceRevoked(String deviceId, String e164) {}
