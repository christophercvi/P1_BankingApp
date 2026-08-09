package com.revature.ccvi.banking.dao;

import java.util.List;
import java.util.Optional;

import com.revature.ccvi.banking.model.Transaction;

/**
 * Persistence contract for the ledger. History is append only; there is no update method by
 * design so recorded activity cannot be rewritten.
 */
public interface TransactionDao {

    Transaction create(Transaction transaction);

    Optional<Transaction> findById(String transactionId);

    List<Transaction> findByAccountId(String accountId, TransactionFilter filter);

    List<Transaction> findByAccountIds(List<String> accountIds, TransactionFilter filter);

    long countByAccountId(String accountId);

    int deleteByAccountId(String accountId);
}
