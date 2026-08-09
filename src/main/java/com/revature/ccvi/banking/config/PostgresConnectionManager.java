package com.revature.ccvi.banking.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import com.revature.ccvi.banking.exception.DataAccessException;

/**
 * Owns JDBC connection creation and the DDL bootstrap for the relational schema. Connections
 * are handed out per operation and closed by the caller through try-with-resources.
 */
public class PostgresConnectionManager {

    private static final String[] SCHEMA_STATEMENTS = {
        """
        CREATE TABLE IF NOT EXISTS customers (
            customer_id     UUID PRIMARY KEY,
            username        VARCHAR(30)  NOT NULL UNIQUE,
            email           VARCHAR(120) NOT NULL UNIQUE,
            first_name      VARCHAR(60)  NOT NULL,
            last_name       VARCHAR(60)  NOT NULL,
            phone           VARCHAR(20),
            password_hash   VARCHAR(200) NOT NULL,
            created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
            last_login_at   TIMESTAMPTZ
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS accounts (
            account_id      UUID PRIMARY KEY,
            account_number  CHAR(10)     NOT NULL UNIQUE,
            customer_id     UUID         NOT NULL REFERENCES customers (customer_id) ON DELETE CASCADE,
            account_type    VARCHAR(10)  NOT NULL CHECK (account_type IN ('CHECKING', 'SAVINGS')),
            status          VARCHAR(10)  NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
            balance         NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
            created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
            closed_at       TIMESTAMPTZ
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS transactions (
            transaction_id           UUID PRIMARY KEY,
            account_id               UUID          NOT NULL REFERENCES accounts (account_id) ON DELETE CASCADE,
            counterparty_account_id  UUID          REFERENCES accounts (account_id) ON DELETE SET NULL,
            transaction_type         VARCHAR(15)   NOT NULL CHECK (transaction_type IN
                                         ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT')),
            amount                   NUMERIC(19,2) NOT NULL CHECK (amount > 0),
            resulting_balance        NUMERIC(19,2) NOT NULL,
            description              VARCHAR(255),
            created_at               TIMESTAMPTZ   NOT NULL DEFAULT now()
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_accounts_customer_id ON accounts (customer_id)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_account_created ON transactions (account_id, created_at DESC)",
        "CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions (transaction_type)"
    };

    private final String url;
    private final String username;
    private final String password;

    public PostgresConnectionManager(AppConfig config) {
        this.url = config.getRequired("db.postgres.url");
        this.username = config.getRequired("db.postgres.username");
        this.password = config.get("db.postgres.password", "");
    }

    PostgresConnectionManager(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public Connection getConnection() {
        Properties credentials = new Properties();
        credentials.setProperty("user", username);
        credentials.setProperty("password", password);
        try {
            Connection connection = DriverManager.getConnection(url, credentials);
            connection.setAutoCommit(true);
            return connection;
        } catch (SQLException ex) {
            throw new DataAccessException("Unable to connect to PostgreSQL at " + url
                    + ". Verify db.postgres.* settings and that the server is running.", ex);
        }
    }

    public void initializeSchema() {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            for (String ddl : SCHEMA_STATEMENTS) {
                statement.execute(ddl);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to initialize the PostgreSQL schema: " + ex.getMessage(), ex);
        }
    }

    public String getUrl() {
        return url;
    }
}
