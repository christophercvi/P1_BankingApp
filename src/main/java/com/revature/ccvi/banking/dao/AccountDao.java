package com.revature.ccvi.banking.dao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountStatus;

/**
 * Persistence contract for bank accounts.
 *
 * <p>{@link #applyBalanceDelta(String, BigDecimal, BigDecimal)} exists so both backends can
 * perform a guarded, atomic credit or debit rather than a read-modify-write round trip that
 * two concurrent sessions could interleave.</p>
 */
public interface AccountDao {

    Account create(Account account);

    Optional<Account> findById(String accountId);

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByCustomerId(String customerId);

    List<Account> findAll();

    boolean updateStatus(String accountId, AccountStatus status, java.time.Instant closedAt);

    /**
     * Atomically adds {@code delta} to the balance, but only if the resulting balance would be
     * at least {@code minimumResultingBalance}.
     *
     * @return the new balance when the update was applied, or empty when the guard rejected it
     */
    Optional<BigDecimal> applyBalanceDelta(String accountId, BigDecimal delta, BigDecimal minimumResultingBalance);

    boolean existsByAccountNumber(String accountNumber);

    boolean deleteById(String accountId);
}
