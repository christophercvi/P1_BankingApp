package com.revature.ccvi.banking.config;

import org.bson.Document;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.revature.ccvi.banking.exception.DataAccessException;

/**
 * Owns the MongoClient lifecycle plus collection and index bootstrap. One client is shared for
 * the process lifetime because the driver pools connections internally.
 */
public class MongoConnectionManager implements AutoCloseable {

    public static final String CUSTOMERS = "customers";
    public static final String ACCOUNTS = "accounts";
    public static final String TRANSACTIONS = "transactions";

    private final MongoClient client;
    private final MongoDatabase database;
    private final boolean transactionsSupported;

    public MongoConnectionManager(AppConfig config) {
        String uri = config.getRequired("db.mongo.uri");
        String databaseName = config.get("db.mongo.database", "bankingdb");
        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri))
                    .readConcern(ReadConcern.MAJORITY)
                    .writeConcern(WriteConcern.MAJORITY)
                    .applyToSocketSettings(builder -> builder.connectTimeout(
                            config.getInt("db.mongo.connectTimeoutMs", 5000), java.util.concurrent.TimeUnit.MILLISECONDS))
                    .applyToClusterSettings(builder -> builder.serverSelectionTimeout(
                            config.getInt("db.mongo.serverSelectionTimeoutMs", 5000),
                            java.util.concurrent.TimeUnit.MILLISECONDS))
                    .build();
            this.client = MongoClients.create(settings);
            this.database = client.getDatabase(databaseName);
            this.transactionsSupported = detectTransactionSupport();
        } catch (MongoException | IllegalArgumentException ex) {
            throw new DataAccessException("Unable to connect to MongoDB using db.mongo.uri. "
                    + "Verify the URI and that the server is reachable.", ex);
        }
    }

    MongoConnectionManager(MongoClient client, MongoDatabase database, boolean transactionsSupported) {
        this.client = client;
        this.database = database;
        this.transactionsSupported = transactionsSupported;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoClient getClient() {
        return client;
    }

    /**
     * Multi document transactions require a replica set or sharded cluster. When running against
     * a standalone server the transfer executor switches to a compensating write strategy.
     */
    public boolean isTransactionsSupported() {
        return transactionsSupported;
    }

    private boolean detectTransactionSupport() {
        try {
            Document result = client.getDatabase("admin").runCommand(new Document("hello", 1));
            return result.containsKey("setName") || "isdbgrid".equals(result.getString("msg"));
        } catch (MongoException ex) {
            return false;
        }
    }

    public void initializeSchema() {
        try {
            database.getCollection(CUSTOMERS).createIndex(Indexes.ascending("username"),
                    new IndexOptions().unique(true).name("uk_customers_username"));
            database.getCollection(CUSTOMERS).createIndex(Indexes.ascending("email"),
                    new IndexOptions().unique(true).name("uk_customers_email"));
            database.getCollection(ACCOUNTS).createIndex(Indexes.ascending("accountNumber"),
                    new IndexOptions().unique(true).name("uk_accounts_number"));
            database.getCollection(ACCOUNTS).createIndex(Indexes.ascending("customerId"),
                    new IndexOptions().name("idx_accounts_customer"));
            database.getCollection(TRANSACTIONS).createIndex(
                    Indexes.compoundIndex(Indexes.ascending("accountId"), Indexes.descending("createdAt")),
                    new IndexOptions().name("idx_transactions_account_created"));
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to initialize MongoDB indexes: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
