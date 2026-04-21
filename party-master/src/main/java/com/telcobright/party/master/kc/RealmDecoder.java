package com.telcobright.party.master.kc;

import com.telcobright.party.master.entity.Operator;
import com.telcobright.party.master.entity.Tenant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

@ApplicationScoped
public class RealmDecoder {

    public static final String OPERATORS_REALM = "party-operators";
    public static final String TENANT_PREFIX   = "tenant-";

    public KcRealm decode(String realmName) {
        if (realmName == null || realmName.isBlank()) {
            throw new IllegalArgumentException("realm is required");
        }
        if (OPERATORS_REALM.equals(realmName)) {
            return new KcRealm(KcRealm.Kind.OPERATORS, null, null);
        }
        if (!realmName.startsWith(TENANT_PREFIX)) {
            throw new IllegalArgumentException("unknown realm " + realmName);
        }
        String[] parts = realmName.substring(TENANT_PREFIX.length()).split("-", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("malformed tenant realm; expected tenant-<opShort>-<tnShort>");
        }
        String opShort = parts[0];
        String tnShort = parts[1];
        Operator op = Operator.find("shortName", opShort).firstResult();
        if (op == null) throw new NotFoundException("operator " + opShort + " not found");
        Tenant t = Tenant.find("operatorId = ?1 and shortName = ?2", op.id, tnShort).firstResult();
        if (t == null) throw new NotFoundException("tenant " + opShort + "/" + tnShort + " not found");
        return new KcRealm(KcRealm.Kind.TENANT, op.id, t.id);
    }

    public static String tenantRealmName(String opShort, String tnShort) {
        return TENANT_PREFIX + opShort + "-" + tnShort;
    }
}
