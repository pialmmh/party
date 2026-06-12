package com.telcobright.party.v2.testkit;

import com.telcobright.party.v2.spi.FacadeDirectory;
import com.telcobright.party.v2.model.E164;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory FacadeDirectory: seed facades; provision() auto-creates active ones. */
public final class FakeFacadeDirectory implements FacadeDirectory {

    private final Map<String, Facade> byE164 = new LinkedHashMap<>();
    private long nextId = 1;

    public FakeFacadeDirectory seed(String e164, String status, String displayName) {
        long id = nextId++;
        byE164.put(e164, new Facade(id, 100 + id, e164,
                E164.digits(e164) + "@localhost", status, displayName));
        return this;
    }

    @Override public Facade provision(String e164, String displayName) {
        return byE164.computeIfAbsent(e164, k -> {
            long id = nextId++;
            return new Facade(id, 100 + id, k, E164.digits(k) + "@localhost", "active", displayName);
        });
    }

    @Override public Optional<Facade> findByE164(String e164) {
        return Optional.ofNullable(byE164.get(e164));
    }

    @Override public List<Facade> searchByE164In(List<String> e164s) {
        List<Facade> out = new ArrayList<>();
        for (String e : e164s) {
            Facade f = byE164.get(e);
            if (f != null) out.add(f);
        }
        return out;
    }
}
