package com.revature.ccvi.banking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revature.ccvi.banking.config.AppConfig;
import com.revature.ccvi.banking.config.DaoFactory;
import com.revature.ccvi.banking.config.DatabaseType;
import com.revature.ccvi.banking.config.PostgresConnectionManager;
import com.revature.ccvi.banking.config.PostgresDaoFactory;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.model.TransactionType;
import com.revature.ccvi.banking.support.DatabaseAvailability;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Runs the shared DAO contract against a live PostgreSQL server. Skipped automatically when no
 * server is reachable, so {@code mvn test} still succeeds on a machine without PostgreSQL.
 */
@DisplayName("PostgreSQL DAO integration")
class PostgresDaoIT extends AbstractDaoContractTest {

    private static PostgresConnectionManager connectionManager;
    private static DaoFactory daoFactory;

    @BeforeAll
    static void connect() {
        assumeTrue(DatabaseAvailability.postgresAvailable(),
                "PostgreSQL is not reachable; skipping the JDBC integration tests");
        AppConfig config = DatabaseAvailability.testConfig();
        connectionManager = new PostgresConnectionManager(config);
        daoFactory = new PostgresDaoFactory(connectionManager);
        daoFactory.initializeSchema();
        truncateAll();
    }

    @AfterAll
    static void disconnect() {
        if (daoFactory != null) {
            truncateAll();
            daoFactory.close();
        }
    }

    private static void truncateAll() {
        try (Connection connection = connectionManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE transactions, accounts, customers CASCADE");
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to reset the PostgreSQL test schema", ex);
        }
    }

    @Override
    protected DaoFactory factory() {
        return daoFactory;
    }

    @Test
    @DisplayName("reports POSTGRES as its database type")
    void reportsDatabaseType() {
        assertEquals(DatabaseType.POSTGRES, daoFactory.getDatabaseType());
        assertNotNull(connectionManager.getUrl());
    }

    @Test
    @DisplayName("the NUMERIC(19,2) check constraint blocks a negative balance")
    void checkConstraintBlocksNegativeBalance() {
        Customer owner = daoFactory.customerDao().create(newCustomer());
        Account account = newAccount(owner.getId(), AccountType.CHECKING, "-5.00");

        assertThrows(DataAccessException.class, () -> daoFactory.accountDao().create(account));
    }

    @Test
    @DisplayName("the foreign key blocks an account for a customer that does not exist")
    void foreignKeyBlocksOrphanAccount() {
        Account orphan = newAccount(UUID.randomUUID().toString(), AccountType.CHECKING, "0.00");

        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> daoFactory.accountDao().create(orphan));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("a transfer to a nonexistent account rolls back and leaves the source untouched")
    void transferToMissingAccountRollsBack() {
        Customer owner = daoFactory.customerDao().create(newCustomer());
        Account source = daoFactory.accountDao().create(newAccount(owner.getId(), AccountType.CHECKING, "100.00"));

        assertThrows(DataAccessException.class, () -> daoFactory.transferExecutor().transfer(
                source.getId(), UUID.randomUUID().toString(), new BigDecimal("10.00"), MoneyUtil.zero(), null));

        assertEquals(new BigDecimal("100.00"),
                daoFactory.accountDao().findById(source.getId()).orElseThrow().getBalance());
        assertEquals(0, daoFactory.transactionDao().countByAccountId(source.getId()));
    }

    @Test
    @DisplayName("locks both rows in a stable order regardless of transfer direction")
    void transfersInBothDirectionsSucceed() {
        Customer owner = daoFactory.customerDao().create(newCustomer());
        Account first = daoFactory.accountDao().create(newAccount(owner.getId(), AccountType.CHECKING, "100.00"));
        Account second = daoFactory.accountDao().create(newAccount(owner.getId(), AccountType.CHECKING, "100.00"));

        assertTrue(daoFactory.transferExecutor().transfer(first.getId(), second.getId(),
                new BigDecimal("30.00"), MoneyUtil.zero(), null).isApplied());
        assertTrue(daoFactory.transferExecutor().transfer(second.getId(), first.getId(),
                new BigDecimal("50.00"), MoneyUtil.zero(), null).isApplied());

        assertEquals(new BigDecimal("120.00"),
                daoFactory.accountDao().findById(first.getId()).orElseThrow().getBalance());
        assertEquals(new BigDecimal("80.00"),
                daoFactory.accountDao().findById(second.getId()).orElseThrow().getBalance());
        assertEquals(2, daoFactory.transactionDao().countByAccountId(first.getId()));
    }

    @Test
    @DisplayName("surfaces a clear message when the connection settings are wrong")
    void reportsBadCredentials() {
        AppConfig broken = AppConfig.fromProperties(propertiesWith(
                "jdbc:postgresql://127.0.0.1:5432/nonexistent_db_xyz", "nobody", "wrong"));
        PostgresConnectionManager badManager = new PostgresConnectionManager(broken);

        DataAccessException ex = assertThrows(DataAccessException.class, badManager::getConnection);
        assertTrue(ex.getMessage().contains("Unable to connect to PostgreSQL"));
    }

    @Test
    @DisplayName("rejects a ledger entry with a non-positive amount")
    void rejectsNonPositiveLedgerAmount() {
        Customer owner = daoFactory.customerDao().create(newCustomer());
        Account account = daoFactory.accountDao().create(newAccount(owner.getId(), AccountType.CHECKING, "0.00"));

        assertThrows(DataAccessException.class, () -> daoFactory.transactionDao().create(newTransaction(
                account.getId(), null, TransactionType.DEPOSIT, "0.00", "0.00", null, java.time.Instant.now())));
    }

    private static java.util.Properties propertiesWith(String url, String username, String password) {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("db.postgres.url", url);
        properties.setProperty("db.postgres.username", username);
        properties.setProperty("db.postgres.password", password);
        return properties;
    }
}
