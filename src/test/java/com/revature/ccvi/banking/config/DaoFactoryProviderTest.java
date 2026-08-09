package com.revature.ccvi.banking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * US-20: the single switch that decides which persistence backend the application runs on.
 * Creating a factory does not open a connection, so these run without a live server.
 */
@DisplayName("DaoFactoryProvider: backend selection from configuration")
class DaoFactoryProviderTest {

    @AfterEach
    void tearDown() {
        AppConfig.reset();
        System.clearProperty("db.type");
    }

    private static AppConfig configFor(String databaseType) {
        Properties properties = new Properties();
        properties.setProperty("db.type", databaseType);
        properties.setProperty("db.postgres.url", "jdbc:postgresql://127.0.0.1:5432/bankingdb");
        properties.setProperty("db.postgres.username", "bankapp");
        properties.setProperty("db.postgres.password", "bankapp_pw");
        properties.setProperty("db.mongo.uri", "mongodb://127.0.0.1:27017");
        properties.setProperty("db.mongo.database", "bankingdb");
        properties.setProperty("db.mongo.serverSelectionTimeoutMs", "500");
        return AppConfig.fromProperties(properties);
    }

    @Test
    @DisplayName("db.type=postgres yields the JDBC factory with a full set of DAOs")
    void selectsPostgres() {
        try (DaoFactory factory = DaoFactoryProvider.create(configFor("postgres"))) {
            assertInstanceOf(PostgresDaoFactory.class, factory);
            assertEquals(DatabaseType.POSTGRES, factory.getDatabaseType());
            assertNotNull(factory.customerDao());
            assertNotNull(factory.accountDao());
            assertNotNull(factory.transactionDao());
            assertNotNull(factory.transferExecutor());
        }
    }

    @Test
    @DisplayName("db.type=mongo yields the document store factory with a full set of DAOs")
    void selectsMongo() {
        try (DaoFactory factory = DaoFactoryProvider.create(configFor("mongo"))) {
            assertInstanceOf(MongoDaoFactory.class, factory);
            assertEquals(DatabaseType.MONGO, factory.getDatabaseType());
            assertNotNull(factory.customerDao());
            assertNotNull(factory.accountDao());
            assertNotNull(factory.transactionDao());
            assertNotNull(factory.transferExecutor());
        }
    }

    @Test
    @DisplayName("the no-argument overload reads the shared configuration singleton")
    void usesSharedConfigByDefault() {
        AppConfig.override(configFor("mongo"));

        try (DaoFactory factory = DaoFactoryProvider.create()) {
            assertEquals(DatabaseType.MONGO, factory.getDatabaseType());
        }
    }

    @Test
    @DisplayName("an unrecognized db.type fails fast rather than defaulting silently")
    void rejectsUnknownBackend() {
        AppConfig config = configFor("cassandra");

        assertThrows(IllegalStateException.class, () -> DaoFactoryProvider.create(config));
    }
}
