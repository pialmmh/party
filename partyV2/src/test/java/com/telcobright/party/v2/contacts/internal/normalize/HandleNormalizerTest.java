package com.telcobright.party.v2.contacts.internal.normalize;

import com.telcobright.party.v2.contacts.spi.Handle;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Raw handle strings → clean, deduped, sorted handles. The client sends E.164. */
class HandleNormalizerTest {

    @Test
    void telNormalizesToE164DespiteFormatting() {
        List<Handle> out = HandleNormalizer.normalize(List.of("+880 1711-000001"));
        assertEquals(1, out.size());
        assertEquals(Handle.TEL, out.get(0).kind());
        assertEquals("+8801711000001", out.get(0).value());
    }

    @Test
    void emailIsLowerCased() {
        List<Handle> out = HandleNormalizer.normalize(List.of("Foo@BAR.com"));
        assertEquals(1, out.size());
        assertEquals(Handle.EMAIL, out.get(0).kind());
        assertEquals("foo@bar.com", out.get(0).value());
    }

    @Test
    void localNumberWithoutPlusIsSkipped() {
        // the client normalizes local → E.164 (it knows the device region); a bare
        // local number reaching the backend is noise, not a handle.
        assertTrue(HandleNormalizer.normalize(List.of("01711000001")).isEmpty());
    }

    @Test
    void duplicatesAndAltFormatsCollapse() {
        List<Handle> out = HandleNormalizer.normalize(List.of("+8801711000001", "008801711000001"));
        assertEquals(1, out.size());
    }

    @Test
    void outputIsSortedByValue() {
        List<Handle> out = HandleNormalizer.normalize(List.of("+8801711000002", "+8801711000001"));
        assertEquals(List.of("+8801711000001", "+8801711000002"),
                out.stream().map(Handle::value).toList());
    }

    @Test
    void garbageAndNullAreSkipped() {
        assertTrue(HandleNormalizer.normalize(Arrays.asList("xyz", "", null)).isEmpty());
    }
}
