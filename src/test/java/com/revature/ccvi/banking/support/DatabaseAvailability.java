package com.revature.ccvi.banking.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Properties;

import com.revature.ccvi.banking.config.AppConfig;

/**
 * Integration test gate. Tests that need a real server call {@link #postgresAvailable()} or
 * {@link #mongoAvailable()} in an assumption, so the suite still passes on a machine that only
 * has one backend (or neither) installed.
 */
public final class DatabaseAvailability {

    private static final int PROBE_TIMEOUT_MS = 1500;

    private DatabaseAvailability() {
    }

    public static AppConfig testConfig() {
        AppConfig fileConfig = AppConfig.load(AppConfig.DEFAULT_RESOURCE);
        Properties properties = new Properties();
        properties.setProperty("db.postgres.url", fileConfig.get("db.postgres.url",
                "jdbc:postgresql://127.0.0.1:5432/bankingdb"));
        properties.setProperty("db.postgres.username", fileConfig.get("db.postgres.username", "bankapp"));
        properties.setProperty("db.postgres.password", fileConfig.get("db.postgres.password", "bankapp_pw"));
        properties.setProperty("db.mongo.uri", fileConfig.get("db.mongo.uri", "mongodb://127.0.0.1:27017"));
        // Integration tests use a dedicated database so they never touch developer data.
        properties.setProperty("db.mongo.database", "bankingdb_test");
        properties.setProperty("db.mongo.serverSelectionTimeoutMs", "2000");
        properties.setProperty("db.mongo.connectTimeoutMs", "2000");
        return AppConfig.fromProperties(properties);
    }

    public static boolean postgresAvailable() {
        String url = testConfig().get("db.postgres.url", "");
        return canConnect(hostOf(url, "127.0.0.1"), portOf(url, 5432));
    }

    public static boolean mongoAvailable() {
        String uri = testConfig().get("db.mongo.uri", "");
        return canConnect(hostOf(uri, "127.0.0.1"), portOf(uri, 27017));
    }

    private static boolean canConnect(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static String hostOf(String connectionString, String fallback) {
        try {
            String cleaned = connectionString.replace("jdbc:postgresql://", "http://")
                    .replaceFirst("^mongodb(\\+srv)?://", "http://");
            String host = URI.create(cleaned).getHost();
            return host == null ? fallback : host;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static int portOf(String connectionString, int fallback) {
        try {
            String cleaned = connectionString.replace("jdbc:postgresql://", "http://")
                    .replaceFirst("^mongodb(\\+srv)?://", "http://");
            int port = URI.create(cleaned).getPort();
            return port == -1 ? fallback : port;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
