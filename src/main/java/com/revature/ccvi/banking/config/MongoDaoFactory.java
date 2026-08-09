package com.revature.ccvi.banking.config;

import com.revature.ccvi.banking.dao.AccountDao;
import com.revature.ccvi.banking.dao.CustomerDao;
import com.revature.ccvi.banking.dao.TransactionDao;
import com.revature.ccvi.banking.dao.TransferExecutor;
import com.revature.ccvi.banking.dao.mongo.MongoAccountDao;
import com.revature.ccvi.banking.dao.mongo.MongoCustomerDao;
import com.revature.ccvi.banking.dao.mongo.MongoTransactionDao;
import com.revature.ccvi.banking.dao.mongo.MongoTransferExecutor;

/**
 * Supplies the MongoDB backed DAO set and owns the shared client.
 */
public class MongoDaoFactory implements DaoFactory {

    private final MongoConnectionManager connectionManager;
    private final CustomerDao customerDao;
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final TransferExecutor transferExecutor;

    public MongoDaoFactory(AppConfig config) {
        this(new MongoConnectionManager(config));
    }

    public MongoDaoFactory(MongoConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.customerDao = new MongoCustomerDao(connectionManager.getDatabase());
        this.accountDao = new MongoAccountDao(connectionManager.getDatabase());
        this.transactionDao = new MongoTransactionDao(connectionManager.getDatabase());
        this.transferExecutor = new MongoTransferExecutor(connectionManager);
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MONGO;
    }

    @Override
    public CustomerDao customerDao() {
        return customerDao;
    }

    @Override
    public AccountDao accountDao() {
        return accountDao;
    }

    @Override
    public TransactionDao transactionDao() {
        return transactionDao;
    }

    @Override
    public TransferExecutor transferExecutor() {
        return transferExecutor;
    }

    @Override
    public void initializeSchema() {
        connectionManager.initializeSchema();
    }

    @Override
    public void close() {
        connectionManager.close();
    }
}
