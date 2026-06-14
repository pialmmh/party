package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.spi.Handle;
import com.telcobright.party.v2.contacts.spi.PartyDirectory;
import com.telcobright.party.v2.spi.FacadeDirectory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Resolves a handle to a person through the Odoo facade directory. A tel maps
 * to a person when an ACTIVE facade exists for it; email is not resolved yet
 * (returns empty — additive when the email channel lands). A directory outage
 * propagates as a {@link com.telcobright.party.v2.model.ProviderException} — a
 * transient miss the caller retries, NOT a non-user.
 */
@ApplicationScoped
public class OdooPartyDirectory implements PartyDirectory {

    @Inject FacadeDirectory facades;

    @Override
    public Optional<PersonRef> resolve(Handle handle) {
        if (!handle.isPhone()) return Optional.empty();
        return facades.findByE164(handle.value())
                .filter(facade -> "active".equals(facade.status()))
                .map(facade -> new PersonRef(personIdOf(facade.partnerId()), facade.displayName()));
    }

    /** Dev mapping: the Odoo partner is the person. A real UUID column lands later. */
    private static String personIdOf(long partnerId) {
        return "p:" + partnerId;
    }
}
