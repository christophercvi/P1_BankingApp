package com.revature.ccvi.banking.dao.mongo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.revature.ccvi.banking.config.MongoConnectionManager;
import com.revature.ccvi.banking.dao.AccountDao;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountStatus;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * MongoDB implementation of {@link AccountDao}.
 *
 * <p>A debit or credit is one {@code findOneAndUpdate} whose filter encodes the minimum balance
 * guard, so the check and the write cannot be interleaved by another session.</p>
 */
public class MongoAccountDao implements AccountDao {

    private static final int DUPLICATE_KEY = 11000;

    private final MongoCollection<Document> collection;

    public MongoAccountDao(MongoDatabase database) {
        this.collection = database.getCollection(MongoConnectionManager.ACCOUNTS);
    }

    @Override
    public Account create(Account account) {
        if (account.getId() == null || account.getId().isBlank()) {
            account.setId(UUID.randomUUID().toString());
        }
        if (account.getCreatedAt() == null) {
            account.setCreatedAt(Instant.now());
        }
        account.setBalance(MoneyUtil.normalize(
                account.getBalance() == null ? MoneyUtil.zero() : account.getBalance()));
        try {
            collection.insertOne(MongoDocumentMapper.toDocument(account));
            return account;
        } catch (MongoWriteException ex) {
            if (ex.getError().getCode() == DUPLICATE_KEY) {
                throw new DataAccessException("That account number is already in use.", ex);
            }
            throw new DataAccessException("Failed to open account: " + ex.getMessage(), ex);
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to open account: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Account> findById(String accountId) {
        return findOne(Filters.eq("_id", accountId));
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return findOne(Filters.eq("accountNumber", accountNumber));
    }

    @Override
    public List<Account> findByCustomerId(String customerId) {
        return findMany(Filters.eq("customerId", customerId));
    }

    @Override
    public List<Account> findAll() {
        return findMany(new Document());
    }

    @Override
    public boolean updateStatus(String accountId, AccountStatus status, Instant closedAt) {
        try {
            return collection.updateOne(Filters.eq("_id", accountId),
                    Updates.combine(
                            Updates.set("status", status.name()),
                            Updates.set("closedAt", MongoDocumentMapper.toDate(closedAt))))
                    .getMatchedCount() == 1;
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to update account status: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<BigDecimal> applyBalanceDelta(String accountId, BigDecimal delta,
                                                  BigDecimal minimumResultingBalance) {
        BigDecimal normalizedDelta = MoneyUtil.normalize(delta);
        BigDecimal floor = MoneyUtil.normalize(
                minimumResultingBalance == null ? MoneyUtil.zero() : minimumResultingBalance);
        // For a debit the required starting balance is floor + |delta|; for a credit the guard is trivially met.
        BigDecimal requiredCurrentBalance = MoneyUtil.normalize(floor.subtract(normalizedDelta));
        Bson filter = Filters.and(
                Filters.eq("_id", accountId),
                Filters.gte("balance", MongoDocumentMapper.toDecimal(requiredCurrentBalance)));
        try {
            Document updated = collection.findOneAndUpdate(filter,
                    Updates.inc("balance", MongoDocumentMapper.toDecimal(normalizedDelta)),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            return Optional.ofNullable(updated).map(document -> MongoDocumentMapper.toBigDecimal(document.get("balance")));
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to update account balance: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean existsByAccountNumber(String accountNumber) {
        try {
            return collection.countDocuments(Filters.eq("accountNumber", accountNumber)) > 0;
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to check account number: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean deleteById(String accountId) {
        try {
            return collection.deleteOne(Filters.eq("_id", accountId)).getDeletedCount() == 1;
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to delete account: " + ex.getMessage(), ex);
        }
    }

    private Optional<Account> findOne(Bson filter) {
        try {
            return Optional.ofNullable(collection.find(filter).first()).map(MongoDocumentMapper::toAccount);
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to load account: " + ex.getMessage(), ex);
        }
    }

    private List<Account> findMany(Bson filter) {
        try {
            List<Account> accounts = new ArrayList<>();
            collection.find(filter).sort(Sorts.ascending("createdAt"))
                    .forEach(document -> accounts.add(MongoDocumentMapper.toAccount(document)));
            return accounts;
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to list accounts: " + ex.getMessage(), ex);
        }
    }
}
