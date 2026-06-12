package com.telcobright.party.v2.contacts.internal.invite;
import com.telcobright.party.v2.contacts.spi.InviteSender;
import com.telcobright.party.v2.contacts.internal.ContactsConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Dev-mode invite delivery: logs server-side, response stays 202 — the wire
 * contract is identical to the SMS-gateway future.
 */
@ApplicationScoped
public class LogInviteSender implements InviteSender {

    private static final Logger LOG = Logger.getLogger(LogInviteSender.class);

    @Inject ContactsConfig cfg;

    @Override
    public void invite(String fromE164, String toE164) {
        if (cfg.invites().devMode()) {
            LOG.infof("Invite (dev mode) %s -> %s", fromE164, toE164);
        } else {
            throw new IllegalStateException(
                    "no SMS gateway configured and invites.dev-mode is off — cannot deliver invite");
        }
    }
}
