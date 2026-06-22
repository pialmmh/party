package com.telcobright.party.v2.threads.internal.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telcobright.party.v2.threads.publishes.ThreadEvent;
import com.telcobright.party.v2.threads.spi.ThreadEventPublisher;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.jboss.logging.Logger;

/**
 * Publishes one {@link ThreadEvent} to its owner's per-account subject on the
 * SL_THREADS JetStream stream — direct CONFIRMED publish (waits for the ack) with
 * an idempotent producer (Nats-Msg-Id = {@code ownerPersonId|conversationId|
 * version}), so a re-publish of the same fact is deduped inside the stream's
 * duplicate window. No outbox: party's DB is the durable source of truth and the
 * HTTP snapshot is the recovery path, so a lost delta self-heals. Mirrors
 * NatsContactEventPublisher.
 *
 * <p>Not a CDI bean itself — {@link ThreadPublisherProvider} constructs it and
 * runs its lifecycle, so dev (NATS off) keeps the log publisher.
 */
public class NatsThreadEventPublisher implements ThreadEventPublisher {

    private static final Logger LOG = Logger.getLogger(NatsThreadEventPublisher.class);

    private final ThreadsNatsConfig cfg;
    private final ObjectMapper json;
    private Connection nc;
    private JetStream js;

    public NatsThreadEventPublisher(ThreadsNatsConfig cfg, ObjectMapper json) {
        this.cfg = cfg;
        this.json = json;
    }

    public void start() {
        try {
            nc = Nats.connect(Options.builder().server(cfg.url())
                    .maxReconnects(-1).connectionName("party-threads").build());
            ThreadStreamSetup.ensure(nc, cfg.stream(), cfg.subjectPrefix() + ">", cfg.maxAgeDays());
            js = nc.jetStream();
            LOG.infof("threads feed publisher up: nats=%s stream=%s", cfg.url(), cfg.stream());
        } catch (Exception e) {
            throw new IllegalStateException("threads NATS connect failed: " + e.getMessage(), e);
        }
    }

    public void stop() {
        try {
            if (nc != null) nc.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void publish(String ownerPersonId, ThreadEvent event) {
        String subject = cfg.subjectPrefix() + subjectToken(ownerPersonId);
        String msgId = ownerPersonId + "|" + event.conversationId() + "|" + event.version();
        try {
            PublishAck ack = js.publish(subject, json.writeValueAsBytes(event),
                    PublishOptions.builder().messageId(msgId).expectedStream(cfg.stream()).build());
            LOG.debugf("threadEvent -> %s %s v%d (seq=%d%s)", subject, event.type(), event.version(),
                    ack.getSeqno(), ack.isDuplicate() ? " dup" : "");
        } catch (Exception e) {
            throw new IllegalStateException("thread event publish failed: " + e.getMessage(), e);
        }
    }

    /**
     * One NATS token per owner — mirrors the chat/contacts rule ([^a-zA-Z0-9]→_)
     * so the sync-proxy and the device compute the SAME subject for an owner.
     */
    static String subjectToken(String ownerPersonId) {
        return ownerPersonId.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
