package com.telcobright.party.v2.model;

import java.util.regex.Pattern;

/**
 * E.164 normalization — byte-identical rules to the Odoo addon's
 * {@code _normalize_e164} (secure_link_facade), so party and Odoo always
 * agree on the canonical form {@code +<digits>}.
 */
public final class E164 {

    private static final Pattern VALID = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private E164() {}

    /** @throws IllegalArgumentException if the input is not a valid E.164 number. */
    public static String normalize(String phone) {
        String digits = (phone == null ? "" : phone).replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
        }
        if (!VALID.matcher(digits).matches()) {
            throw new IllegalArgumentException("not a valid E.164 phone number");
        }
        return digits;
    }

    /** The JID local part: the digits without the leading '+'. */
    public static String digits(String e164) {
        return e164.startsWith("+") ? e164.substring(1) : e164;
    }
}
