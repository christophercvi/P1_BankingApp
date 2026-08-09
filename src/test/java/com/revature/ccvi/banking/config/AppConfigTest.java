package com.revature.ccvi.banking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Configuration layer: property loading and database selection (US-20)")
class AppConfigTest {

    @AfterEach
    void tearDown() {
        AppConfig.reset();
        System.clearProperty("db.type");
        System.clearProperty("bank.savings.minimumBalance");
    }

    @Test
    @DisplayName("loads the packaged application.properties from the classpath")
    void loadsPackagedProperties() {
        AppConfig config = AppConfig.load(AppConfig.DEFAULT_RESOURCE);

        assertNotNull(config.get("db.postgres.url"));
        assertNotNull(config.get("db.mongo.uri"));
        assertNotNull(config.getDatabaseType());
    }

    @Test
    @DisplayName("returns an empty configuration for a resource that does not exist")
    void toleratesMissingResource() {
        AppConfig config = AppConfig.load("no-such-file.properties");

        assertNull(config.get("db.type"));
        assertEquals("fallback", config.get("db.type", "fallback"));
    }

    @Test
    @DisplayName("reads values from an in-memory Properties object")
    void readsSuppliedProperties() {
        Properties properties = new Properties();
        properties.setProperty("db.type", "mongo");
        properties.setProperty("some.count", "7");
        properties.setProperty("some.flag", "true");
        properties.setProperty("some.money", "12.34");

        AppConfig config = AppConfig.fromProperties(properties);

        assertEquals(DatabaseType.MONGO, config.getDatabaseType());
        assertEquals(7, config.getInt("some.count", 1));
        assertTrue(config.getBoolean("some.flag", false));
        assertEquals(new BigDecimal("12.34"), config.getDecimal("some.money", "0.00"));
    }

    @Test
    @DisplayName("applies defaults for absent keys")
    void appliesDefaults() {
        AppConfig config = AppConfig.fromProperties(new Properties());

        assertEquals(42, config.getInt("absent.int", 42));
        assertTrue(config.getBoolean("absent.flag", true));
        assertFalse(config.getBoolean("absent.flag", false));
        assertEquals(new BigDecimal("9.99"), config.getDecimal("absent.money", "9.99"));
        assertEquals(DatabaseType.POSTGRES, config.getDatabaseType());
    }

    @Test
    @DisplayName("treats a blank value as absent")
    void blankValuesAreAbsent() {
        Properties properties = new Properties();
        properties.setProperty("blank.key", "   ");

        assertNull(AppConfig.fromProperties(properties).get("blank.key"));
    }

    @Test
    @DisplayName("lets a system property override the file value")
    void systemPropertyOverridesFile() {
        Properties properties = new Properties();
        properties.setProperty("db.type", "postgres");
        System.setProperty("db.type", "mongo");

        assertEquals(DatabaseType.MONGO, AppConfig.fromProperties(properties).getDatabaseType());
    }

    @Test
    @DisplayName("throws a clear error for a missing required property")
    void requiredPropertyMustExist() {
        AppConfig config = AppConfig.fromProperties(new Properties());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> config.getRequired("db.postgres.url"));
        assertTrue(ex.getMessage().contains("db.postgres.url"));
    }

    @Test
    @DisplayName("throws when a numeric property cannot be parsed")
    void rejectsNonNumericValues() {
        Properties properties = new Properties();
        properties.setProperty("bad.int", "abc");
        properties.setProperty("bad.money", "abc");
        AppConfig config = AppConfig.fromProperties(properties);

        assertThrows(IllegalStateException.class, () -> config.getInt("bad.int", 0));
        assertThrows(IllegalStateException.class, () -> config.getDecimal("bad.money", "0.00"));
    }

    @Test
    @DisplayName("maps property names to environment variable names")
    void mapsEnvironmentKeys() {
        assertEquals("DB_POSTGRES_PASSWORD", AppConfig.toEnvironmentKey("db.postgres.password"));
        assertEquals("BANK_SAVINGS_MINIMUMBALANCE", AppConfig.toEnvironmentKey("bank.savings.minimumBalance"));
        assertEquals("SOME_DASHED_KEY", AppConfig.toEnvironmentKey("some-dashed.key"));
    }

    @Test
    @DisplayName("exposes a shared singleton that tests can replace")
    void singletonCanBeOverridden() {
        Properties properties = new Properties();
        properties.setProperty("db.type", "mongo");
        AppConfig replacement = AppConfig.fromProperties(properties);

        AppConfig.override(replacement);

        assertSame(replacement, AppConfig.getInstance());
        AppConfig.reset();
        assertNotNull(AppConfig.getInstance());
    }

    @ParameterizedTest(name = "\"{0}\" resolves to {1}")
    @CsvSource({"postgres,POSTGRES", "PostgreSQL,POSTGRES", "pg,POSTGRES", "jdbc,POSTGRES",
                "mongo,MONGO", "MongoDB,MONGO", " Mongo ,MONGO"})
    void parsesDatabaseTypeAliases(String raw, DatabaseType expected) {
        assertEquals(expected, DatabaseType.from(raw));
    }

    @ParameterizedTest(name = "rejects db.type \"{0}\"")
    @ValueSource(strings = {"mysql", "oracle", "", "   "})
    void rejectsUnknownDatabaseType(String raw) {
        assertThrows(IllegalStateException.class, () -> DatabaseType.from(raw));
    }

    @Test
    @DisplayName("rejects a null db.type")
    void rejectsNullDatabaseType() {
        assertThrows(IllegalStateException.class, () -> DatabaseType.from(null));
    }
}
