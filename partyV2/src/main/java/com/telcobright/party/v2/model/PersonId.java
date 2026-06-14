package com.telcobright.party.v2.model;

/**
 * The global person key — a JSContact uid. Dev mapping: derived from the Odoo
 * partner (a real opaque uid column lands later, with zero consumer impact since
 * the {@code p:} namespace is opaque to everyone downstream). Defined once here
 * so the token minter and the contacts directory agree.
 */
public final class PersonId {

    private PersonId() {}

    public static String of(long partnerId) {
        return "p:" + partnerId;
    }
}
