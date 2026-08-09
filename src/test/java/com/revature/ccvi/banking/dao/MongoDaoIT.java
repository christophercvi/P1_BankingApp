package com.revature.ccvi.banking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.model.Filters;
import com.revature.ccvi.banking.config.AppConfig;
import com.revature.ccvi.banking.config.DaoFactory;
import com.revature.ccvi.banking.config.DatabaseType;
import com.revature.ccvi.banking.config.MongoConnectionManager;
import com.revature.ccvi.banking.config.MongoDaoFactory;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.support.DatabaseAvailability;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Runs the shared DAO contract against a live MongoDB server, plus a few document specific
 * assertions (Decimal128 storage, index creation). Skipped when no server is reachable.
 */
@DisplayName("MongoDB DAO integration")
class MongoDaoIT extends AbstractDaoContractTest {

    private static MongoConnectionManager connectionManager;
    private static DaoFactory daoFactory;

    @BeforeAll
    static void connect() {
        assumeTrue(DatabaseAvailability.mongoAvailable(),
                "MongoDB is not reachable; skipping the document store integration tests");
        AppConfig config = DatabaseAvailability.testConfig();
        connectionManager = new MongoConnectionManager(config);
        daoFactory = new MongoDaoFactory(connectionManager);
        daoFactory.initializeSchema();
        clearCollections();
    }

    @AfterAll
    static void disconnect() {
        if (daoFactory != null) {
            clearCollections();
            daoFactory.close();
        }
    }

    private static void clearCollections() {
        connectionManager.getDatabase().getCollection(MongoConnectionManager.TRANSACTIONS)
                .deleteMany(new Document());
        connectionManager.getDatabase().getCollection(MongoConnectionManager.ACCOUNTS)
                .deleteMany(new Document());
        connectionManager.getDatabase().getCollection(MongoConnectionManager.CUSTOMERS)
                .deleteMany(new Document());
    }

    @Override
    protected DaoFactory factory() {
        return daoFactory;
    }

    @Test
    @DisplayName("reports MONGO as its database type")
    void reportsDatabaseType() {
        assertEquals(DatabaseType.MONGO, daoFactory.getDatabaseType());
        assertNotNull(connectionManager.getClient());
        assertNotNull(connectionManager.getDatabase());
    }

    @Test
    @DisplayName("creates the unique and compound indexes described in the schema design")
    void createsIndexes() {
        List<String> accountIndexes = indexNames(MongoConnectionManager.ACCOUNTS);
        List<String> customerIndexes = indexNames(MongoConnectionManager.CUSTOMERS);
        List<String> transactionIndexes = indexNames(MongoConnectionManager.TRANSACTIONS);

        assertTrue(customerIndexes.contains("uk_customers_username"));
        assertTrue(customerIndexes.contains("uk_customers_email"));
        assertTrue(accountIndexes.contains("uk_accounts_number"));
        assertTrue(accountIndexes.contains("idx_accounts_customer"));
        assertTrue(transactionIndexes.contains("idx_transactions_account_created"));
    }

    @Test
    @DisplayName("stores monetary fields as Decimal128 so decimal precision is exact")
    void storesMoneyAsDecimal128() {
        Customer owner = daoFactory.customerDao().create(newCustomer());
        Account account = daoFactory.accountDao().create(
                newAccount(owner.getId(), AccountType.CHECKING, "1234.56"));

        Document stored = connectionManager.getDatabase()
                .getCollection(MongoConnectionManager.ACCOUNTS)
                .find(Filters.eq("_id", account.getId())).first();

        assertNotNull(stored);
        assertTrue(stored.get("balance") instanceof Decimal128);
        assertEquals(new BigDecimal("1234.56"),
                ((Decimal128) stored.get("balance")).bigDecimalValue().setScale(2));
    }

    @Test
    @DisplayName("repeated increments keep exact decimal arithmetic")
    void incrementsStayExact() {
        Customer owner = daoFactory.customerDao().create(newCustomer());
        Account account = daoFactory.accountDao().create(newAccount(owner.getId(), AccountType.CHECKING, "0.00"));

        for (int index = 0; index < 10; index++) {
            daoFactory.accountDao().applyBalanceDelta(account.getId(), new BigDecimal("0.10"), MoneyUtil.zero());
        }

        assertEquals(new BigDecimal("1.00"),
                daoFactory.accountDao().findById(account.getId()).orElseThrow().getBalance());
    }

    @Test
    @DisplayName("a transfer to a nonexistent document does not leave the source debited")
    void transferToMissingAccountDoesNotLoseMoney() {
        Customer owner = daoFactory.customerDao().create(newCustomer());
        Account source = daoFactory.accountDao().create(newAccount(owner.getId(), AccountType.CHECKING, "100.00"));

        assertThrows(DataAccessException.class, () -> daoFactory.transferExecutor().transfer(
                source.getId(), UUID.randomUUID().toString(), new BigDecimal("10.00"), MoneyUtil.zero(), null));

        assertEquals(new BigDecimal("100.00"),
                daoFactory.accountDao().findById(source.getId()).orElseThrow().getBalance());
    }

    @Test
    @DisplayName("surfaces a clear message for an unreachable server")
    void reportsUnreachableServer() {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("db.mongo.uri", "mongodb://127.0.0.1:1");
        properties.setProperty("db.mongo.database", "bankingdb_unreachable");
        properties.setProperty("db.mongo.serverSelectionTimeoutMs", "300");
        AppConfig broken = AppConfig.fromProperties(properties);

        MongoConnectionManager manager = new MongoConnectionManager(broken);
        try {
            DataAccessException ex = assertThrows(DataAccessException.class, manager::initializeSchema);
            assertTrue(ex.getMessage().contains("MongoDB"));
        } finally {
            manager.close();
        }
    }

    @Test
    @DisplayName("rejects a malformed connection URI")
    void rejectsMalformedUri() {
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("db.mongo.uri", "not-a-mongodb-uri");
        AppConfig broken = AppConfig.fromProperties(properties);

        assertThrows(DataAccessException.class, () -> new MongoConnectionManager(broken));
    }

    private static List<String> indexNames(String collection) {
        List<String> names = new ArrayList<>();
        connectionManager.getDatabase().getCollection(collection).listIndexes()
                .forEach(index -> names.add(index.getString("name")));
        return names;
    }
}
