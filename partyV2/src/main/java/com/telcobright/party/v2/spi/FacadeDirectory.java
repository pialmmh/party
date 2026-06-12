package com.telcobright.party.v2.spi;

import java.util.List;
import java.util.Optional;

/**
 * The secure-link facade directory — who is on the app, and provision a
 * verified phone onto it (frozen §2/§6). SHARED module-root port (architect
 * ruling): registration provisions through it, contacts resolves matches
 * through it. The production impl is the Odoo secure_link.facade gateway
 * (providers/odoo); tests hand in a fake.
 */
public interface FacadeDirectory {

    /** One facade row: the E.164→partner/JID mapping. status: active|suspended|closed. */
    record Facade(long facadeId, long partnerId, String e164, String jid,
                  String status, String displayName) {}

    /** Find-or-create partner + facade for a verified phone (idempotent). */
    Facade provision(String e164, String displayName);

    /** Read-only lookup — does this phone have a facade (i.e. is it on the app)? */
    Optional<Facade> findByE164(String e164);

    /** Batch read for contact sync: which of these numbers have facades. */
    List<Facade> searchByE164In(List<String> e164s);
}
