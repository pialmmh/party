package com.telcobright.party.v2.registration.internal.registry;

import com.telcobright.party.v2.registration.spi.DeviceRegistryStore;
import com.telcobright.party.v2.registration.spi.DeviceRegistryStore.DeviceRow;
import com.telcobright.party.v2.testkit.Beans;
import com.telcobright.party.v2.testkit.LocalMysql;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SQL tier: the real MySQL adapter against the dev box's instance (skipped if absent). */
class JdbcDeviceRegistryStoreSqlTest {

    private JdbcDeviceRegistryStore store;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(LocalMysql.available(), "local MySQL not reachable — SQL tier skipped");
        LocalMysql.dropTable("device_registry");
        store = new JdbcDeviceRegistryStore();
        Beans.set(store, "ds", LocalMysql.ds());
    }

    @Test
    void upsert_find_rotate_status_roundTrip() {
        store.upsertActive("device-A-0001", 101, "+8801711000001", "hash-1");

        DeviceRow row = store.findByDeviceId("device-A-0001").orElseThrow();
        assertEquals(DeviceRegistryStore.ACTIVE, row.status());
        assertEquals(101, row.partnerId());
        assertTrue(store.findByRefreshTokenHash("hash-1").isPresent());

        store.rotateRefreshToken("device-A-0001", "hash-2");
        assertTrue(store.findByRefreshTokenHash("hash-1").isEmpty(), "old hash is dead");
        assertTrue(store.findByRefreshTokenHash("hash-2").isPresent());

        store.setStatus("device-A-0001", DeviceRegistryStore.REVOKED);
        assertEquals(DeviceRegistryStore.REVOKED,
                store.findByDeviceId("device-A-0001").orElseThrow().status());
    }

    @Test
    void reRegistration_reclaimsTheDeviceId_backToActive() {
        store.upsertActive("device-A-0001", 101, "+8801711000001", "hash-1");
        store.setStatus("device-A-0001", DeviceRegistryStore.REVOKED);

        store.upsertActive("device-A-0001", 202, "+8801711000002", "hash-9");

        DeviceRow row = store.findByDeviceId("device-A-0001").orElseThrow();
        assertEquals(DeviceRegistryStore.ACTIVE, row.status());
        assertEquals(202, row.partnerId());
        assertEquals("+8801711000002", row.e164());
        assertEquals("hash-9", row.refreshTokenHash());
    }

    @Test
    void listByE164_returnsAllOfTheAccountsDevices() {
        store.upsertActive("device-A-0001", 101, "+8801711000001", "hash-1");
        store.upsertActive("device-B-0002", 101, "+8801711000001", "hash-2");
        store.upsertActive("device-C-0003", 102, "+8801711000002", "hash-3");

        assertEquals(2, store.listByE164("+8801711000001").size());
        assertEquals(1, store.listByE164("+8801711000002").size());
        assertTrue(store.listByE164("+8801711000099").isEmpty());
    }
}
