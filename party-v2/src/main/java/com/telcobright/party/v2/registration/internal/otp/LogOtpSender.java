package com.telcobright.party.v2.registration.internal.otp;
import com.telcobright.party.v2.registration.spi.OtpSender;
import com.telcobright.party.v2.registration.internal.RegistrationConfig;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Dev-mode delivery (user directive: authless e2e now, no SMS gateway):
 * logs the code server-side. The API response NEVER carries the code —
 * the wire contract is identical to production.
 */
@ApplicationScoped
public class LogOtpSender implements OtpSender {

    private static final Logger LOG = Logger.getLogger(LogOtpSender.class);

    @Inject RegistrationConfig cfg;

    @Override
    public void send(String phone, String code) {
        if (cfg.otp().devMode()) {
            LOG.infof("OTP (dev mode) for %s: %s", phone, code);
        } else {
            // No SMS gateway wired yet — fail loudly rather than silently dropping codes.
            throw new IllegalStateException(
                    "no SMS gateway configured and otp.dev-mode is off — cannot deliver OTP");
        }
    }
}
