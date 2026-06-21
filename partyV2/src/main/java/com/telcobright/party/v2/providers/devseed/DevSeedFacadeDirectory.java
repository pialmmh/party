package com.telcobright.party.v2.providers.devseed;

import com.telcobright.party.v2.model.E164;
import com.telcobright.party.v2.spi.FacadeDirectory;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A dev-only {@link FacadeDirectory}: resolves a fixed seed list of E.164 numbers
 * to synthetic ACTIVE facades, so contact owner-resolution + the WS contact
 * scenarios (is-a-user / 2-device) run before the real Odoo {@code secure_link.facade}
 * addon is installed in the deployed Odoo. Enabled ONLY when the build property
 * {@code securelink.devseed.enabled=true} (the it_vm dev jar, passed by
 * remote-deploy-v2.sh); production keeps the Odoo {@code @DefaultBean}. Never in prod.
 * (The knobs live OUTSIDE the {@code party.v2.*} namespace on purpose: that prefix is a
 * {@code @ConfigMapping} root, and any unmapped key under it fails validation, SRCFG00050.)
 *
 * <p>The synthetic partnerId is the numeric value of the number's digits, so the
 * resulting personId ({@code p:<digits>}) is stable across devices and replays —
 * what the 2-device convergence scenario needs. Unseeded numbers return empty
 * (a clean non-user), which is what the non-user scenario asserts.
 */
@ApplicationScoped
@IfBuildProperty(name = "securelink.devseed.enabled", stringValue = "true")
public class DevSeedFacadeDirectory implements FacadeDirectory {

    /** Comma-separated E.164 numbers seeded as active app users (runtime config). */
    @ConfigProperty(name = "securelink.devseed.numbers")
    Optional<List<String>> seedNumbers;

    private final Map<String, Facade> byE164 = new ConcurrentHashMap<>();

    @PostConstruct
    void seed() {
        for (String raw : seedNumbers.orElse(List.of())) {
            String e164 = safeNorm(raw == null ? "" : raw.trim());
            if (e164 != null) byE164.put(e164, facadeFor(e164, "Dev User " + E164.digits(e164)));
        }
    }

    @Override
    public Facade provision(String e164, String displayName) {
        String n = E164.normalize(e164);
        return byE164.computeIfAbsent(n,
                k -> facadeFor(k, displayName == null ? "Dev User " + E164.digits(k) : displayName));
    }

    @Override
    public Optional<Facade> findByE164(String e164) {
        String n = safeNorm(e164);
        return n == null ? Optional.empty() : Optional.ofNullable(byE164.get(n));
    }

    @Override
    public List<Facade> searchByE164In(List<String> e164s) {
        List<Facade> out = new ArrayList<>();
        for (String e : e164s) {
            String n = safeNorm(e);
            Facade f = n == null ? null : byE164.get(n);
            if (f != null) out.add(f);
        }
        return out;
    }

    /**
     * DEV ONLY: a seeded number logs in with ANY password (mirrors
     * {@code otp.dev-mode}); unseeded numbers are rejected. This whole bean is
     * {@code @IfBuildProperty securelink.devseed.enabled=true} — it never ships
     * to prod, where {@link com.telcobright.party.v2.providers.odoo.OdooFacadeClient}
     * does the real pbkdf2 check in Odoo.
     */
    @Override
    public Optional<Facade> checkCredentials(String e164, String password) {
        return findByE164(e164);
    }

    private static Facade facadeFor(String e164, String displayName) {
        long partnerId = Long.parseLong(E164.digits(e164));   // stable synthetic id per number
        return new Facade(partnerId, partnerId, e164, E164.digits(e164) + "@localhost", "active", displayName);
    }

    private static String safeNorm(String e) {
        try {
            return E164.normalize(e);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
