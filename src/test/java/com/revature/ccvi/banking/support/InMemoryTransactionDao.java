package com.revature.ccvi.banking.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.revature.ccvi.banking.dao.TransactionDao;
import com.revature.ccvi.banking.dao.TransactionFilter;
import com.revature.ccvi.banking.model.Transaction;

/**
 * In-memory {@link TransactionDao} that applies the shared {@link TransactionFilter} semantics,
 * so filter behaviour verified here matches what the SQL and BSON translations must produce.
 */
public class InMemoryTransactionDao implements TransactionDao {

    private final List<Transaction> store = new ArrayList<>();

    @Override
    public Transaction create(Transaction transaction) {
        if (transaction.getId() == null || transaction.getId().isBlank()) {
            transaction.setId(UUID.randomUUID().toString());
        }
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(Instant.now());
        }
        store.add(copy(transaction));
        return transaction;
    }

    @Override
    public Optional<Transaction> findById(String transactionId) {
        return store.stream()
                .filter(transaction -> transaction.getId().equals(transactionId))
                .findFirst()
                .map(this::copy);
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
        List<Transaction> matches = store.stream()
                .filter(transaction -> accountIds.contains(transaction.getAccountId()))
                .filter(transaction -> effective.getType()
                        .map(type -> type == transaction.getType()).orElse(true))
                .filter(transaction -> effective.getFrom()
                        .map(from -> !transaction.getCreatedAt().isBefore(from)).orElse(true))
                .filter(transaction -> effective.getTo()
                        .map(to -> !transaction.getCreatedAt().isAfter(to)).orElse(true))
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed()
                        .thenComparing(Transaction::getId, Comparator.reverseOrder()))
                .map(this::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (effective.hasLimit() && matches.size() > effective.getLimit()) {
            return List.copyOf(matches.subList(0, effective.getLimit()));
        }
        return List.copyOf(matches);
    }

    @Override
    public long countByAccountId(String accountId) {
        return store.stream().filter(transaction -> transaction.getAccountId().equals(accountId)).count();
    }

    @Override
    public int deleteByAccountId(String accountId) {
        int before = store.size();
        store.removeIf(transaction -> transaction.getAccountId().equals(accountId));
        return before - store.size();
    }

    public int size() {
        return store.size();
    }

    public List<Transaction> all() {
        return store.stream().map(this::copy).toList();
    }

    private Transaction copy(Transaction source) {
        return new Transaction(source.getId(), source.getAccountId(), source.getCounterpartyAccountId(),
                source.getType(), source.getAmount(), source.getResultingBalance(), source.getDescription(),
                source.getCreatedAt());
    }
}
