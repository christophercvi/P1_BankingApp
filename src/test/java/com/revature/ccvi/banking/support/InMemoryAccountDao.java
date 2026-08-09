package com.revature.ccvi.banking.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.revature.ccvi.banking.dao.AccountDao;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountStatus;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * In-memory {@link AccountDao}. The guarded balance update mirrors the real implementations: the
 * write is applied only when the resulting balance clears the supplied floor.
 */
public class InMemoryAccountDao implements AccountDao {

    private final Map<String, Account> store = new LinkedHashMap<>();

    public boolean failNextWrite;

    @Override
    public Account create(Account account) {
        if (failNextWrite) {
            failNextWrite = false;
            throw new DataAccessException("Simulated database outage.");
        }
        if (account.getId() == null || account.getId().isBlank()) {
            account.setId(UUID.randomUUID().toString());
        }
        if (account.getCreatedAt() == null) {
            account.setCreatedAt(Instant.now());
        }
        account.setBalance(MoneyUtil.normalize(
                account.getBalance() == null ? MoneyUtil.zero() : account.getBalance()));
        if (existsByAccountNumber(account.getAccountNumber())) {
            throw new DataAccessException("That account number is already in use.");
        }
        store.put(account.getId(), copy(account));
        return account;
    }

    @Override
    public Optional<Account> findById(String accountId) {
        return Optional.ofNullable(store.get(accountId)).map(this::copy);
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return store.values().stream()
                .filter(account -> account.getAccountNumber().equals(accountNumber))
                .findFirst()
                .map(this::copy);
    }

    @Override
    public List<Account> findByCustomerId(String customerId) {
        return store.values().stream()
                .filter(account -> account.isOwnedBy(customerId))
                .map(this::copy)
                .toList();
    }

    @Override
    public List<Account> findAll() {
        return new ArrayList<>(store.values()).stream().map(this::copy).toList();
    }

    @Override
    public boolean updateStatus(String accountId, AccountStatus status, Instant closedAt) {
        Account stored = store.get(accountId);
        if (stored == null) {
            return false;
        }
        stored.setStatus(status);
        stored.setClosedAt(closedAt);
        return true;
    }

    @Override
    public Optional<BigDecimal> applyBalanceDelta(String accountId, BigDecimal delta,
                                                  BigDecimal minimumResultingBalance) {
        Account stored = store.get(accountId);
        if (stored == null) {
            return Optional.empty();
        }
        BigDecimal floor = MoneyUtil.normalize(
                minimumResultingBalance == null ? MoneyUtil.zero() : minimumResultingBalance);
        BigDecimal candidate = MoneyUtil.normalize(stored.getBalance().add(MoneyUtil.normalize(delta)));
        if (candidate.compareTo(floor) < 0) {
            return Optional.empty();
        }
        stored.setBalance(candidate);
        return Optional.of(candidate);
    }

    @Override
    public boolean existsByAccountNumber(String accountNumber) {
        return store.values().stream().anyMatch(account -> account.getAccountNumber().equals(accountNumber));
    }

    @Override
    public boolean deleteById(String accountId) {
        return store.remove(accountId) != null;
    }

    /** Direct balance read used by assertions, bypassing the ownership checks in the services. */
    public BigDecimal balanceOf(String accountId) {
        Account stored = store.get(accountId);
        return stored == null ? null : stored.getBalance();
    }

    public int size() {
        return store.size();
    }

    private Account copy(Account source) {
        return new Account(source.getId(), source.getAccountNumber(), source.getCustomerId(), source.getType(),
                source.getStatus(), source.getBalance(), source.getCreatedAt(), source.getClosedAt());
    }
}
