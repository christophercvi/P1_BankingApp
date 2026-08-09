package com.revature.ccvi.banking.config;

import com.revature.ccvi.banking.dao.AccountDao;
import com.revature.ccvi.banking.dao.CustomerDao;
import com.revature.ccvi.banking.dao.TransactionDao;
import com.revature.ccvi.banking.dao.TransferExecutor;

/**
 * Abstract factory that hands the service layer a complete set of DAOs for whichever backend
 * configuration selected. Services depend on this interface only, never on a concrete factory.
 */
public interface DaoFactory extends AutoCloseable {

    DatabaseType getDatabaseType();

    CustomerDao customerDao();

    AccountDao accountDao();

    TransactionDao transactionDao();

    TransferExecutor transferExecutor();

    /** Creates schema objects, collections, and indexes when they do not already exist. */
    void initializeSchema();

    @Override
    void close();
}
