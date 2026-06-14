package com.telcobright.party.v2.contacts.internal.nats;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;

import java.time.Duration;

/**
 * Provisions the SL_CONTACTS JetStream stream if absent — idempotent and
 * ADDITIVE: it creates only its own stream and never touches SL_CHAT (architect
 * ruling). A Limits-retention buffer (snapshot is the recovery path); the
 * duplicate window backs the idempotent producer (Nats-Msg-Id dedup).
 */
final class ContactStreamSetup {

    private ContactStreamSetup() {}

    static void ensure(Connection nc, String name, String subjectFilter, long maxAgeDays) {
        try {
            JetStreamManagement jsm = nc.jetStreamManagement();
            try {
                jsm.getStreamInfo(name);
                return;   // already there — leave it as-is
            } catch (JetStreamApiException notFound) {
                // fall through to create
            }
            jsm.addStream(StreamConfiguration.builder()
                    .name(name)
                    .subjects(subjectFilter)
                    .storageType(StorageType.File)
                    .retentionPolicy(RetentionPolicy.Limits)
                    .maxAge(Duration.ofDays(maxAgeDays))
                    .duplicateWindow(Duration.ofMinutes(30))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("ensure stream " + name + " failed: " + e.getMessage(), e);
        }
    }
}
