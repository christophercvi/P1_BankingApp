package com.revature.ccvi.banking.config;

/**
 * The single switch point between persistence technologies (US-20). Nothing above the
 * configuration layer references a concrete factory, so flipping {@code db.type} in
 * application.properties changes the entire data access stack.
 */
public final class DaoFactoryProvider {

    private DaoFactoryProvider() {
    }

    public static DaoFactory create() {
        return create(AppConfig.getInstance());
    }

    public static DaoFactory create(AppConfig config) {
        return create(config, config.getDatabaseType());
    }

    public static DaoFactory create(AppConfig config, DatabaseType type) {
        return switch (type) {
            case POSTGRES -> new PostgresDaoFactory(config);
            case MONGO -> new MongoDaoFactory(config);
        };
    }
}
