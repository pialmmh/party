package com.telcobright.party.v2.testkit;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;

import java.sql.Connection;
import java.sql.Statement;

/**
 * SQL-tier test support: a programmatic pool on the dev box's MySQL
 * (127.0.0.1:3306, the lxc instance), database party_v2_test. Tests guard with
 * {@code Assumptions.assumeTrue(LocalMysql.available())} so the suite stays
 * green on boxes without MySQL.
 */
public final class LocalMysql {

    private static final String URL =
            "jdbc:mysql://127.0.0.1:3306/party_v2_test?createDatabaseIfNotExist=true"
                    + "&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=2000";

    private static AgroalDataSource ds;
    private static Boolean reachable;

    private LocalMysql() {}

    public static synchronized boolean available() {
        if (reachable == null) {
            try {
                ds = AgroalDataSource.from(new AgroalDataSourceConfigurationSupplier()
                        .connectionPoolConfiguration(cp -> cp
                                .initialSize(1).maxSize(16)
                                .connectionFactoryConfiguration(cf -> cf
                                        .jdbcUrl(URL)
                                        .principal(new NamePrincipal("root"))
                                        .credential(new SimplePassword("123456")))));
                try (Connection c = ds.getConnection()) {
                    reachable = c.isValid(2);
                }
            } catch (Exception e) {
                reachable = false;
            }
        }
        return reachable;
    }

    public static AgroalDataSource ds() {
        if (!available()) throw new IllegalStateException("local MySQL not reachable");
        return ds;
    }

    /** Fresh start: drop the table so the store's lazy ensureSchema recreates it. */
    public static void dropTable(String table) {
        try (Connection c = ds().getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + table);
        } catch (Exception e) {
            throw new IllegalStateException("drop " + table + " failed", e);
        }
    }
}
