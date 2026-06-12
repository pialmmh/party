package com.telcobright.party.v2.contacts.internal.match;
import com.telcobright.party.v2.contacts.internal.Denied;

import com.telcobright.party.v2.model.E164;
import com.telcobright.party.v2.model.ProviderException;
import com.telcobright.party.v2.spi.FacadeDirectory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only phonebook matcher (frozen §6 POST /contacts/sync): which of these
 * numbers is an active secure-link user. Hits the Odoo facade only — it never
 * reads or writes the contact graph, so it carries no owner identity.
 */
@ApplicationScoped
public class ContactMatcher {

    private static final Logger LOG = Logger.getLogger(ContactMatcher.class);

    public record Match(String e164, String jid, String displayName) {}
    public record SyncResult(List<Match> matches, List<String> nonUsers) {}

    @Inject FacadeDirectory facades;

    /** Match a raw phonebook against the facade; unparseable numbers are skipped, not failed. */
    public SyncResult match(List<String> numbers) {
        Set<String> normalized = normalizeLenient(numbers);
        Map<String, FacadeDirectory.Facade> byE164 = facadesFor(List.copyOf(normalized));
        List<Match> matches = new ArrayList<>();
        List<String> nonUsers = new ArrayList<>();
        for (String e164 : normalized) {
            FacadeDirectory.Facade f = byE164.get(e164);
            if (f != null && "active".equals(f.status())) {
                matches.add(new Match(f.e164(), f.jid(), f.displayName()));
            } else {
                nonUsers.add(e164);
            }
        }
        return new SyncResult(matches, nonUsers);
    }

    // ── named steps ───────────────────────────────────────────────────────

    private Set<String> normalizeLenient(List<String> numbers) {
        Set<String> out = new LinkedHashSet<>();
        for (String n : numbers == null ? List.<String>of() : numbers) {
            try {
                out.add(E164.normalize(n));
            } catch (IllegalArgumentException ignored) {
                // unparseable phonebook noise — skip, don't fail the sync
            }
        }
        return out;
    }

    private Map<String, FacadeDirectory.Facade> facadesFor(List<String> e164s) {
        try {
            Map<String, FacadeDirectory.Facade> byE164 = new HashMap<>();
            for (FacadeDirectory.Facade f : facades.searchByE164In(e164s)) {
                byE164.put(f.e164(), f);
            }
            return byE164;
        } catch (ProviderException e) {
            LOG.error("facade lookup failed: " + e.getMessage());
            throw Denied.unavailable("contact match unavailable");
        }
    }
}
