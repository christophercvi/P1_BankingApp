package com.revature.ccvi.banking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revature.ccvi.banking.config.DaoFactory;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountStatus;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.model.Transaction;
import com.revature.ccvi.banking.model.TransactionType;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * One shared test body run against both DAO implementations (US-21). Because PostgreSQL and
 * MongoDB inherit these cases from the same class, any behavioural drift between the two backends
 * fails the build.
 */
abstract class AbstractDaoContractTest {

    protected abstract DaoFactory factory();

    private CustomerDao customers() {
        return factory().customerDao();
    }

    private AccountDao accounts() {
        return factory().accountDao();
    }

    private TransactionDao transactions() {
        return factory().transactionDao();
    }

    @Test
    @DisplayName("creates a customer and reads it back by id, username, and email")
    void customerCrud() {
        Customer created = customers().create(newCustomer());

        assertNotNull(created.getId());
        Customer byId = customers().findById(created.getId()).orElseThrow();
        assertEquals(created.getUsername(), byId.getUsername());
        assertEquals(created.getEmail(), byId.getEmail());
        assertEquals(created.getPasswordHash(), byId.getPasswordHash());
        assertNotNull(byId.getCreatedAt());
        assertNull(byId.getLastLoginAt());
        assertEquals(created.getId(), customers().findByUsername(created.getUsername()).orElseThrow().getId());
        assertEquals(created.getId(), customers().findByEmail(created.getEmail()).orElseThrow().getId());
        assertTrue(customers().existsByUsername(created.getUsername()));
        assertTrue(customers().existsByEmail(created.getEmail()));
        assertFalse(customers().findAll().isEmpty());
    }

    @Test
    @DisplayName("returns empty for unknown customer lookups")
    void customerLookupMisses() {
        assertTrue(customers().findById(UUID.randomUUID().toString()).isEmpty());
        assertTrue(customers().findById("not-a-valid-id").isEmpty());
        assertTrue(customers().findByUsername("nobody_" + suffix()).isEmpty());
        assertTrue(customers().findByEmail("nobody_" + suffix() + "@example.com").isEmpty());
        assertFalse(customers().existsByUsername("nobody_" + suffix()));
        assertFalse(customers().existsByEmail("nobody_" + suffix() + "@example.com"));
    }

    @Test
    @DisplayName("rejects a duplicate username or email at the storage layer")
    void enforcesCustomerUniqueness() {
        Customer first = customers().create(newCustomer());
        Customer duplicate = newCustomer();
        duplicate.setUsername(first.getUsername());

        assertThrows(DataAccessException.class, () -> customers().create(duplicate));
    }

    @Test
    @DisplayName("updates the customer profile, password hash, and login timestamp")
    void updatesCustomer() {
        Customer created = customers().create(newCustomer());
        String newEmail = "updated_" + suffix() + "@example.com";
        created.setEmail(newEmail);
        created.setFirstName("Updated");
        created.setLastName("Person");
        created.setPhone("555-4321");

        assertTrue(customers().update(created));
        Customer reloaded = customers().findById(created.getId()).orElseThrow();
        assertEquals(newEmail, reloaded.getEmail());
        assertEquals("Updated", reloaded.getFirstName());
        assertEquals("555-4321", reloaded.getPhone());

        assertTrue(customers().updatePasswordHash(created.getId(), "new-hash"));
        assertEquals("new-hash", customers().findById(created.getId()).orElseThrow().getPasswordHash());

        Instant loginTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        assertTrue(customers().touchLastLogin(created.getId(), loginTime));
        assertNotNull(customers().findById(created.getId()).orElseThrow().getLastLoginAt());
    }

    @Test
    @DisplayName("reports false when updating or deleting a customer that does not exist")
    void updateMissingCustomer() {
        Customer ghost = newCustomer();
        ghost.setId(UUID.randomUUID().toString());

        assertFalse(customers().update(ghost));
        assertFalse(customers().updatePasswordHash(ghost.getId(), "hash"));
        assertFalse(customers().touchLastLogin(ghost.getId(), Instant.now()));
        assertFalse(customers().deleteById(ghost.getId()));
    }

    @Test
    @DisplayName("creates an account and reads it back by id, number, and owner")
    void accountCrud() {
        Customer owner = customers().create(newCustomer());
        Account created = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "150.00"));

        Account byId = accounts().findById(created.getId()).orElseThrow();
        assertEquals(created.getAccountNumber(), byId.getAccountNumber());
        assertEquals(AccountType.CHECKING, byId.getType());
        assertEquals(AccountStatus.ACTIVE, byId.getStatus());
        assertEquals(new BigDecimal("150.00"), byId.getBalance());
        assertNotNull(byId.getCreatedAt());
        assertNull(byId.getClosedAt());
        assertEquals(created.getId(),
                accounts().findByAccountNumber(created.getAccountNumber()).orElseThrow().getId());
        assertEquals(1, accounts().findByCustomerId(owner.getId()).size());
        assertTrue(accounts().existsByAccountNumber(created.getAccountNumber()));
        assertFalse(accounts().findAll().isEmpty());
    }

    @Test
    @DisplayName("returns empty for unknown account lookups")
    void accountLookupMisses() {
        assertTrue(accounts().findById(UUID.randomUUID().toString()).isEmpty());
        assertTrue(accounts().findById("not-a-valid-id").isEmpty());
        assertTrue(accounts().findByAccountNumber("0000000000").isEmpty());
        assertTrue(accounts().findByCustomerId(UUID.randomUUID().toString()).isEmpty());
        assertTrue(accounts().findByCustomerId("not-a-valid-id").isEmpty());
        assertFalse(accounts().existsByAccountNumber("0000000000"));
    }

    @Test
    @DisplayName("rejects a duplicate account number")
    void enforcesAccountNumberUniqueness() {
        Customer owner = customers().create(newCustomer());
        Account first = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "0.00"));
        Account duplicate = newAccount(owner.getId(), AccountType.SAVINGS, "0.00");
        duplicate.setAccountNumber(first.getAccountNumber());

        assertThrows(DataAccessException.class, () -> accounts().create(duplicate));
    }

    @Test
    @DisplayName("updates account status and stores the closure timestamp")
    void updatesAccountStatus() {
        Customer owner = customers().create(newCustomer());
        Account account = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "0.00"));
        Instant closedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        assertTrue(accounts().updateStatus(account.getId(), AccountStatus.CLOSED, closedAt));

        Account reloaded = accounts().findById(account.getId()).orElseThrow();
        assertEquals(AccountStatus.CLOSED, reloaded.getStatus());
        assertNotNull(reloaded.getClosedAt());
        assertFalse(accounts().updateStatus(UUID.randomUUID().toString(), AccountStatus.FROZEN, null));
    }

    @Test
    @DisplayName("applies a credit atomically and returns the new balance")
    void appliesCredit() {
        Customer owner = customers().create(newCustomer());
        Account account = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "10.00"));

        BigDecimal balance = accounts()
                .applyBalanceDelta(account.getId(), new BigDecimal("15.50"), MoneyUtil.zero())
                .orElseThrow();

        assertEquals(new BigDecimal("25.50"), balance);
        assertEquals(new BigDecimal("25.50"), accounts().findById(account.getId()).orElseThrow().getBalance());
    }

    @Test
    @DisplayName("rejects a debit that would breach the balance floor and leaves the balance intact")
    void rejectsGuardedDebit() {
        Customer owner = customers().create(newCustomer());
        Account account = accounts().create(newAccount(owner.getId(), AccountType.SAVINGS, "100.00"));

        Optional<BigDecimal> rejected = accounts().applyBalanceDelta(
                account.getId(), new BigDecimal("-90.00"), new BigDecimal("25.00"));

        assertTrue(rejected.isEmpty());
        assertEquals(new BigDecimal("100.00"), accounts().findById(account.getId()).orElseThrow().getBalance());

        BigDecimal allowed = accounts().applyBalanceDelta(
                account.getId(), new BigDecimal("-75.00"), new BigDecimal("25.00")).orElseThrow();
        assertEquals(new BigDecimal("25.00"), allowed);
    }

    @Test
    @DisplayName("returns empty when applying a delta to an account that does not exist")
    void deltaOnMissingAccount() {
        assertTrue(accounts().applyBalanceDelta(UUID.randomUUID().toString(),
                new BigDecimal("5.00"), MoneyUtil.zero()).isEmpty());
    }

    @Test
    @DisplayName("appends transactions and reads them back newest first")
    void transactionCrud() {
        Customer owner = customers().create(newCustomer());
        Account account = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "0.00"));
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        Transaction deposit = transactions().create(newTransaction(account.getId(), null,
                TransactionType.DEPOSIT, "100.00", "100.00", "first", base.minusSeconds(60)));
        Transaction withdrawal = transactions().create(newTransaction(account.getId(), null,
                TransactionType.WITHDRAWAL, "40.00", "60.00", "second", base));

        List<Transaction> history = transactions().findByAccountId(account.getId(), TransactionFilter.none());

        assertEquals(2, history.size());
        assertEquals(withdrawal.getId(), history.get(0).getId(), "newest entry first");
        assertEquals(deposit.getId(), history.get(1).getId());
        assertEquals(new BigDecimal("40.00"), history.get(0).getAmount());
        assertEquals(new BigDecimal("60.00"), history.get(0).getResultingBalance());
        assertEquals("second", history.get(0).getDescription());
        assertEquals(2, transactions().countByAccountId(account.getId()));
        assertEquals(deposit.getId(), transactions().findById(deposit.getId()).orElseThrow().getId());
    }

    @Test
    @DisplayName("returns empty for unknown transaction lookups")
    void transactionLookupMisses() {
        assertTrue(transactions().findById(UUID.randomUUID().toString()).isEmpty());
        assertTrue(transactions().findById("not-a-valid-id").isEmpty());
        assertTrue(transactions().findByAccountIds(List.of(), TransactionFilter.none()).isEmpty());
        assertTrue(transactions().findByAccountIds(null, TransactionFilter.none()).isEmpty());
        assertEquals(0, transactions().countByAccountId(UUID.randomUUID().toString()));
    }

    @Test
    @DisplayName("filters history by type, date range, and row limit identically on both backends")
    void filtersHistory() {
        Customer owner = customers().create(newCustomer());
        Account account = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "0.00"));
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        transactions().create(newTransaction(account.getId(), null, TransactionType.DEPOSIT,
                "100.00", "100.00", null, base.minus(10, ChronoUnit.DAYS)));
        transactions().create(newTransaction(account.getId(), null, TransactionType.WITHDRAWAL,
                "25.00", "75.00", null, base.minus(1, ChronoUnit.DAYS)));
        transactions().create(newTransaction(account.getId(), null, TransactionType.DEPOSIT,
                "10.00", "85.00", null, base));

        List<Transaction> depositsOnly = transactions().findByAccountId(account.getId(),
                TransactionFilter.builder().type(TransactionType.DEPOSIT).build());
        assertEquals(2, depositsOnly.size());
        assertTrue(depositsOnly.stream().allMatch(entry -> entry.getType() == TransactionType.DEPOSIT));

        List<Transaction> recent = transactions().findByAccountId(account.getId(),
                TransactionFilter.builder().from(base.minus(2, ChronoUnit.DAYS)).to(base).build());
        assertEquals(2, recent.size());

        List<Transaction> limited = transactions().findByAccountId(account.getId(),
                TransactionFilter.builder().limit(1).build());
        assertEquals(1, limited.size());

        List<Transaction> everything = transactions().findByAccountId(account.getId(),
                TransactionFilter.builder().unlimited().build());
        assertEquals(3, everything.size());

        List<Transaction> nullFilter = transactions().findByAccountId(account.getId(), null);
        assertEquals(3, nullFilter.size());
    }

    @Test
    @DisplayName("combines history across several accounts")
    void combinesHistoryAcrossAccounts() {
        Customer owner = customers().create(newCustomer());
        Account first = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "0.00"));
        Account second = accounts().create(newAccount(owner.getId(), AccountType.SAVINGS, "0.00"));
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        transactions().create(newTransaction(first.getId(), null, TransactionType.DEPOSIT,
                "5.00", "5.00", null, base.minusSeconds(30)));
        transactions().create(newTransaction(second.getId(), null, TransactionType.DEPOSIT,
                "7.00", "7.00", null, base));

        List<Transaction> combined = transactions().findByAccountIds(
                List.of(first.getId(), second.getId()), TransactionFilter.builder().unlimited().build());

        assertEquals(2, combined.size());
    }

    @Test
    @DisplayName("stores the counterparty reference on transfer ledger entries")
    void storesCounterparty() {
        Customer owner = customers().create(newCustomer());
        Account source = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "50.00"));
        Account destination = accounts().create(newAccount(owner.getId(), AccountType.SAVINGS, "0.00"));

        transactions().create(newTransaction(source.getId(), destination.getId(),
                TransactionType.TRANSFER_OUT, "20.00", "30.00", "memo", Instant.now()));

        Transaction stored = transactions()
                .findByAccountId(source.getId(), TransactionFilter.none()).get(0);
        assertEquals(destination.getId(), stored.getCounterpartyAccountId());
    }

    @Test
    @DisplayName("US-15 commits a transfer as one unit and writes both ledger entries")
    void transferIsAtomicAndBalanced() {
        Customer owner = customers().create(newCustomer());
        Account source = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "200.00"));
        Account destination = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "50.00"));

        TransferExecutor.TransferOutcome outcome = factory().transferExecutor().transfer(
                source.getId(), destination.getId(), new BigDecimal("125.00"), MoneyUtil.zero(), "rent split");

        assertTrue(outcome.isApplied());
        assertEquals(new BigDecimal("75.00"), outcome.getSourceBalance());
        assertEquals(new BigDecimal("175.00"), outcome.getDestinationBalance());
        assertEquals(new BigDecimal("75.00"), accounts().findById(source.getId()).orElseThrow().getBalance());
        assertEquals(new BigDecimal("175.00"), accounts().findById(destination.getId()).orElseThrow().getBalance());

        Transaction outEntry = transactions().findByAccountId(source.getId(), TransactionFilter.none()).get(0);
        Transaction inEntry = transactions().findByAccountId(destination.getId(), TransactionFilter.none()).get(0);
        assertEquals(TransactionType.TRANSFER_OUT, outEntry.getType());
        assertEquals(TransactionType.TRANSFER_IN, inEntry.getType());
        assertEquals(destination.getId(), outEntry.getCounterpartyAccountId());
        assertEquals(source.getId(), inEntry.getCounterpartyAccountId());
        assertEquals("rent split", outEntry.getDescription());
    }

    @Test
    @DisplayName("rejects an underfunded transfer without moving money or writing a ledger entry")
    void rejectsUnderfundedTransfer() {
        Customer owner = customers().create(newCustomer());
        Account source = accounts().create(newAccount(owner.getId(), AccountType.SAVINGS, "100.00"));
        Account destination = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "0.00"));

        TransferExecutor.TransferOutcome outcome = factory().transferExecutor().transfer(
                source.getId(), destination.getId(), new BigDecimal("90.00"), new BigDecimal("25.00"), null);

        assertFalse(outcome.isApplied());
        assertNull(outcome.getSourceBalance());
        assertEquals(new BigDecimal("100.00"), accounts().findById(source.getId()).orElseThrow().getBalance());
        assertEquals(new BigDecimal("0.00"), accounts().findById(destination.getId()).orElseThrow().getBalance());
        assertEquals(0, transactions().countByAccountId(source.getId()));
        assertEquals(0, transactions().countByAccountId(destination.getId()));
    }

    @Test
    @DisplayName("deletes transactions and accounts")
    void deletesRecords() {
        Customer owner = customers().create(newCustomer());
        Account account = accounts().create(newAccount(owner.getId(), AccountType.CHECKING, "0.00"));
        transactions().create(newTransaction(account.getId(), null, TransactionType.DEPOSIT,
                "5.00", "5.00", null, Instant.now()));

        assertEquals(1, transactions().deleteByAccountId(account.getId()));
        assertEquals(0, transactions().countByAccountId(account.getId()));
        assertTrue(accounts().deleteById(account.getId()));
        assertFalse(accounts().deleteById(account.getId()));
        assertTrue(customers().deleteById(owner.getId()));
    }

    protected Customer newCustomer() {
        String tag = suffix();
        Customer customer = new Customer();
        customer.setUsername("user_" + tag);
        customer.setEmail("user_" + tag + "@example.com");
        customer.setFirstName("Integration");
        customer.setLastName("Tester");
        customer.setPhone("555-0000");
        customer.setPasswordHash("$argon2id$dummy$hash");
        customer.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        return customer;
    }

    protected Account newAccount(String customerId, AccountType type, String balance) {
        Account account = new Account();
        account.setAccountNumber(randomAccountNumber());
        account.setCustomerId(customerId);
        account.setType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(new BigDecimal(balance));
        account.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        return account;
    }

    protected Transaction newTransaction(String accountId, String counterpartyId, TransactionType type,
                                         String amount, String resultingBalance, String description,
                                         Instant createdAt) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(accountId);
        transaction.setCounterpartyAccountId(counterpartyId);
        transaction.setType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setResultingBalance(new BigDecimal(resultingBalance));
        transaction.setDescription(description);
        transaction.setCreatedAt(createdAt.truncatedTo(ChronoUnit.MILLIS));
        return transaction;
    }

    private static String randomAccountNumber() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
