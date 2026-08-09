package com.revature.ccvi.banking.dao.mongo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.revature.ccvi.banking.config.MongoConnectionManager;
import com.revature.ccvi.banking.dao.TransactionDao;
import com.revature.ccvi.banking.dao.TransactionFilter;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Transaction;

/**
 * MongoDB implementation of {@link TransactionDao}. The shared {@link TransactionFilter} is
 * translated into a BSON query so history filtering behaves identically on both backends.
 */
public class MongoTransactionDao implements TransactionDao {

    private final MongoCollection<Document> collection;

    public MongoTransactionDao(MongoDatabase database) {
        this.collection = database.getCollection(MongoConnectionManager.TRANSACTIONS);
    }

    @Override
    public Transaction create(Transaction transaction) {
        if (transaction.getId() == null || transaction.getId().isBlank()) {
            transaction.setId(UUID.randomUUID().toString());
        }
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(Instant.now());
        }
        try {
            collection.insertOne(MongoDocumentMapper.toDocument(transaction));
            return transaction;
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to record transaction: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Transaction> findById(String transactionId) {
        try {
            return Optional.ofNullable(collection.find(Filters.eq("_id", transactionId)).first())
                    .map(MongoDocumentMapper::toTransaction);
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to load transaction: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Transaction> findByAccountId(String accountId, TransactionFilter filter) {
        return findByAccountIds(List.of(accountId), filter);
    }

    @Override
    public List<Transaction> findByAccountIds(List<String> accountIds, TransactionFilter filter) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }
        TransactionFilter effective = filter == null ? TransactionFilter.none() : filter;
        List<Bson> clauses = new ArrayList<>();
        clauses.add(Filters.in("accountId", accountIds));
        effective.getType().ifPresent(type -> clauses.add(Filters.eq("transactionType", type.name())));
        effective.getFrom().ifPresent(from -> clauses.add(Filters.gte("createdAt", MongoDocumentMapper.toDate(from))));
        effective.getTo().ifPresent(to -> clauses.add(Filters.lte("createdAt", MongoDocumentMapper.toDate(to))));

        try {
            FindIterable<Document> cursor = collection.find(Filters.and(clauses))
                    .sort(Sorts.orderBy(Sorts.descending("createdAt"), Sorts.descending("_id")));
            if (effective.hasLimit()) {
                cursor = cursor.limit(effective.getLimit());
            }
            List<Transaction> transactions = new ArrayList<>();
            cursor.forEach(document -> transactions.add(MongoDocumentMapper.toTransaction(document)));
            return transactions;
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to load transaction history: " + ex.getMessage(), ex);
        }
    }

    @Override
    public long countByAccountId(String accountId) {
        try {
            return collection.countDocuments(Filters.eq("accountId", accountId));
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to count transactions: " + ex.getMessage(), ex);
        }
    }

    @Override
    public int deleteByAccountId(String accountId) {
        try {
            return (int) collection.deleteMany(Filters.eq("accountId", accountId)).getDeletedCount();
        } catch (MongoException ex) {
            throw new DataAccessException("Failed to delete transactions: " + ex.getMessage(), ex);
        }
    }
}
