package com.revature.ccvi.banking.config;

/**
 * Supported persistence backends, chosen by the {@code db.type} configuration property.
 */
public enum DatabaseType {
    POSTGRES,
    MONGO;

    public static DatabaseType from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("db.type must be set to 'postgres' or 'mongo'");
        }
        String normalized = raw.trim().toLowerCase();
        return switch (normalized) {
            case "postgres", "postgresql", "pg", "jdbc" -> POSTGRES;
            case "mongo", "mongodb" -> MONGO;
            default -> throw new IllegalStateException(
                    "Unsupported db.type '" + raw + "'. Use 'postgres' or 'mongo'.");
        };
    }
}
