package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.spi.Handle;
import com.telcobright.party.v2.model.E164;

import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Turns raw handle strings into clean {@link Handle}s: phone numbers to E.164,
 * emails to lower-case. Output is deduplicated and sorted by value, so the same
 * contact always normalizes to the same ordered set (the basis for a stable
 * contactId). Unparseable phone-book noise is skipped, never fatal — and a
 * local-format number with no {@code +} is treated as noise: the client sends
 * E.164 (it knows the device region).
 */
public final class HandleNormalizer {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private HandleNormalizer() {}

    public static List<Handle> normalize(List<String> raw) {
        TreeMap<String, Handle> byValue = new TreeMap<>();   // sorted + deduped by value
        for (String r : raw == null ? List.<String>of() : raw) {
            Handle h = normalizeOne(r);
            if (h != null) byValue.putIfAbsent(h.value(), h);
        }
        return List.copyOf(byValue.values());
    }

    private static Handle normalizeOne(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        try {
            return Handle.phone(E164.normalize(trimmed));
        } catch (IllegalArgumentException notAPhone) {
            // not E.164 — try email next
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return EMAIL.matcher(lower).matches() ? Handle.email(lower) : null;
    }
}
