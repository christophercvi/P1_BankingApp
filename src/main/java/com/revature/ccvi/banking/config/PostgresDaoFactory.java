package com.revature.ccvi.banking.config;

import com.revature.ccvi.banking.dao.AccountDao;
import com.revature.ccvi.banking.dao.CustomerDao;
import com.revature.ccvi.banking.dao.TransactionDao;
import com.revature.ccvi.banking.dao.TransferExecutor;
import com.revature.ccvi.banking.dao.postgres.PostgresAccountDao;
import com.revature.ccvi.banking.dao.postgres.PostgresCustomerDao;
import com.revature.ccvi.banking.dao.postgres.PostgresTransactionDao;
import com.revature.ccvi.banking.dao.postgres.PostgresTransferExecutor;

/**
 * Supplies the JDBC backed DAO set.
 */
public class PostgresDaoFactory implements DaoFactory {

    private final PostgresConnectionManager connectionManager;
    private final CustomerDao customerDao;
    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final TransferExecutor transferExecutor;

    public PostgresDaoFactory(AppConfig config) {
        this(new PostgresConnectionManager(config));
    }

    public PostgresDaoFactory(PostgresConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.customerDao = new PostgresCustomerDao(connectionManager);
        this.accountDao = new PostgresAccountDao(connectionManager);
        this.transactionDao = new PostgresTransactionDao(connectionManager);
        this.transferExecutor = new PostgresTransferExecutor(connectionManager);
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRES;
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
        // Connections are per operation and closed by the DAOs; nothing is held open here.
    }
}
