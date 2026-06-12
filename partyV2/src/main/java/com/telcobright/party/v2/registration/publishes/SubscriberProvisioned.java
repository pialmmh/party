package com.telcobright.party.v2.registration.publishes;

/**
 * Emitted when OTP verification provisions (or re-activates) a device for a
 * subscriber — the facade now resolves to a partner and the device can mint
 * tokens. (frozen §2 publishes/)
 */
public record SubscriberProvisioned(long partnerId, String e164, String jid, String deviceId) {}
