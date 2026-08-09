package com.revature.ccvi.banking.config;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads {@code application.properties} from the classpath, then lets environment variables
 * override any value so credentials can stay out of the repository (US-20).
 *
 * <p>A property named {@code db.postgres.password} maps to the environment variable
 * {@code DB_POSTGRES_PASSWORD}.</p>
 */
public class AppConfig {

    public static final String DEFAULT_RESOURCE = "application.properties";

    private static AppConfig instance;

    private final Properties properties;

    AppConfig(Properties properties) {
        this.properties = properties;
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = load(DEFAULT_RESOURCE);
        }
        return instance;
    }

    /** Test seam: replaces the singleton so suites can run against in-memory settings. */
    public static synchronized void override(AppConfig replacement) {
        instance = replacement;
    }

    public static synchronized void reset() {
        instance = null;
    }

    public static AppConfig load(String resourceName) {
        Properties loaded = new Properties();
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (stream != null) {
                loaded.load(stream);
            } else {
                Path external = Path.of(resourceName);
                if (Files.exists(external)) {
                    try (InputStream fileStream = Files.newInputStream(external)) {
                        loaded.load(fileStream);
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read configuration resource: " + resourceName, ex);
        }
        return new AppConfig(loaded);
    }

    public static AppConfig fromProperties(Properties properties) {
        Properties copy = new Properties();
        copy.putAll(properties);
        return new AppConfig(copy);
    }

    public String get(String key) {
        String environmentValue = System.getenv(toEnvironmentKey(key));
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    public String getRequired(String key) {
        String value = get(key);
        if (value == null) {
            throw new IllegalStateException("Missing required configuration property: " + key);
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Configuration property " + key + " must be an integer", ex);
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public BigDecimal getDecimal(String key, String defaultValue) {
        String value = get(key, defaultValue);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Configuration property " + key + " must be a decimal number", ex);
        }
    }

    public DatabaseType getDatabaseType() {
        return DatabaseType.from(get("db.type", "postgres"));
    }

    static String toEnvironmentKey(String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase();
    }
}
