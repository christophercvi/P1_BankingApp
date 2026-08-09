package com.revature.ccvi.banking.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("Model layer: encapsulation, identity, and enum parsing")
class ModelTest {

    @Test
    @DisplayName("Customer exposes its fields through accessors and hides the password hash from toString")
    void customerAccessors() {
        Instant created = Instant.now();
        Customer customer = new Customer("id-1", "jdoe", "j@example.com", "Jane", "Doe",
                "555-0000", "hash-value", created, null);

        assertEquals("id-1", customer.getId());
        assertEquals("jdoe", customer.getUsername());
        assertEquals("j@example.com", customer.getEmail());
        assertEquals("Jane Doe", customer.getFullName());
        assertEquals("555-0000", customer.getPhone());
        assertEquals("hash-value", customer.getPasswordHash());
        assertEquals(created, customer.getCreatedAt());
        assertFalse(customer.toString().contains("hash-value"));
    }

    @Test
    @DisplayName("Customer setters round-trip and identity is based on id")
    void customerIdentity() {
        Customer first = new Customer();
        first.setId("same");
        first.setUsername("a");
        first.setEmail("a@example.com");
        first.setFirstName("A");
        first.setLastName("B");
        first.setPhone("555-1111");
        first.setPasswordHash("h");
        first.setCreatedAt(Instant.EPOCH);
        first.setLastLoginAt(Instant.EPOCH);

        Customer second = new Customer();
        second.setId("same");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first, first);
        assertNotEquals(first, "not a customer");
        second.setId("different");
        assertNotEquals(first, second);
        assertEquals(Instant.EPOCH, first.getLastLoginAt());
    }

    @Test
    @DisplayName("Customer tolerates absent name parts when building a display name")
    void customerFullNameWithNulls() {
        Customer customer = new Customer();

        assertEquals(" ", customer.getFullName());
    }

    @Test
    @DisplayName("Account reports ownership and transactability")
    void accountBehaviour() {
        Instant created = Instant.now();
        Account account = new Account("acct-1", "1234567890", "cust-1", AccountType.CHECKING,
                AccountStatus.ACTIVE, new BigDecimal("10.00"), created, null);

        assertTrue(account.isOwnedBy("cust-1"));
        assertFalse(account.isOwnedBy("cust-2"));
        assertFalse(account.isOwnedBy(null));
        assertTrue(account.isTransactable());
        assertEquals("1234567890", account.getAccountNumber());
        assertEquals(AccountType.CHECKING, account.getType());
        assertEquals(created, account.getCreatedAt());
        assertTrue(account.toString().contains("1234567890"));

        account.setStatus(AccountStatus.FROZEN);
        assertFalse(account.isTransactable());
        account.setStatus(null);
        assertFalse(account.isTransactable());
    }

    @Test
    @DisplayName("Account setters round-trip and identity is based on id")
    void accountIdentity() {
        Account account = new Account();
        account.setId("acct-9");
        account.setAccountNumber("9999999999");
        account.setCustomerId("cust-9");
        account.setType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.CLOSED);
        account.setBalance(BigDecimal.ZERO);
        account.setCreatedAt(Instant.EPOCH);
        account.setClosedAt(Instant.EPOCH);

        Account other = new Account();
        other.setId("acct-9");

        assertEquals(account, other);
        assertEquals(account.hashCode(), other.hashCode());
        assertEquals(account, account);
        assertNotEquals(account, new Object());
        assertEquals(Instant.EPOCH, account.getClosedAt());
        assertEquals("cust-9", account.getCustomerId());
    }

    @Test
    @DisplayName("Account with an unowned customer id is not owned by anyone")
    void accountWithoutOwner() {
        Account account = new Account();

        assertFalse(account.isOwnedBy("anyone"));
    }

    @Test
    @DisplayName("Transaction reports a signed amount from the owning account's perspective")
    void transactionSignedAmount() {
        Transaction deposit = new Transaction("t1", "acct-1", null, TransactionType.DEPOSIT,
                new BigDecimal("25.00"), new BigDecimal("125.00"), "memo", Instant.now());
        Transaction withdrawal = new Transaction("t2", "acct-1", null, TransactionType.WITHDRAWAL,
                new BigDecimal("25.00"), new BigDecimal("100.00"), null, Instant.now());
        Transaction transferIn = new Transaction("t3", "acct-1", "acct-2", TransactionType.TRANSFER_IN,
                new BigDecimal("10.00"), new BigDecimal("110.00"), null, Instant.now());

        assertEquals(new BigDecimal("25.00"), deposit.getSignedAmount());
        assertEquals(new BigDecimal("-25.00"), withdrawal.getSignedAmount());
        assertEquals(new BigDecimal("10.00"), transferIn.getSignedAmount());
        assertEquals("acct-2", transferIn.getCounterpartyAccountId());
        assertEquals(BigDecimal.ZERO, new Transaction().getSignedAmount());
        assertTrue(deposit.toString().contains("DEPOSIT"));
    }

    @Test
    @DisplayName("Transaction setters round-trip and identity is based on id")
    void transactionIdentity() {
        Transaction transaction = new Transaction();
        transaction.setId("t-9");
        transaction.setAccountId("acct-9");
        transaction.setCounterpartyAccountId("acct-8");
        transaction.setType(TransactionType.TRANSFER_OUT);
        transaction.setAmount(new BigDecimal("1.00"));
        transaction.setResultingBalance(new BigDecimal("2.00"));
        transaction.setDescription("memo");
        transaction.setCreatedAt(Instant.EPOCH);

        Transaction other = new Transaction();
        other.setId("t-9");

        assertEquals(transaction, other);
        assertEquals(transaction.hashCode(), other.hashCode());
        assertEquals(transaction, transaction);
        assertNotEquals(transaction, "nope");
        assertEquals("memo", transaction.getDescription());
        assertEquals("acct-9", transaction.getAccountId());
        assertEquals(Instant.EPOCH, transaction.getCreatedAt());
    }

    @ParameterizedTest(name = "AccountType.from parses \"{0}\"")
    @CsvSource({"checking,CHECKING", "SAVINGS,SAVINGS", " Checking ,CHECKING"})
    void parsesAccountType(String raw, AccountType expected) {
        assertEquals(expected, AccountType.from(raw));
    }

    @Test
    @DisplayName("AccountType.from rejects unknown and null values")
    void rejectsBadAccountType() {
        assertThrows(IllegalArgumentException.class, () -> AccountType.from("brokerage"));
        assertThrows(IllegalArgumentException.class, () -> AccountType.from(null));
    }

    @ParameterizedTest(name = "AccountStatus.from parses \"{0}\"")
    @CsvSource({"active,ACTIVE", "FROZEN,FROZEN", " closed ,CLOSED"})
    void parsesAccountStatus(String raw, AccountStatus expected) {
        assertEquals(expected, AccountStatus.from(raw));
    }

    @Test
    @DisplayName("only ACTIVE accounts are transactable")
    void statusTransactability() {
        assertTrue(AccountStatus.ACTIVE.isTransactable());
        assertFalse(AccountStatus.FROZEN.isTransactable());
        assertFalse(AccountStatus.CLOSED.isTransactable());
        assertThrows(IllegalArgumentException.class, () -> AccountStatus.from(null));
        assertThrows(IllegalArgumentException.class, () -> AccountStatus.from("unknown"));
    }

    @ParameterizedTest(name = "{0} credit flag")
    @EnumSource(TransactionType.class)
    void transactionTypeCreditFlags(TransactionType type) {
        boolean expectedCredit = type == TransactionType.DEPOSIT || type == TransactionType.TRANSFER_IN;

        assertEquals(expectedCredit, type.isCredit());
        assertEquals(type, TransactionType.from(type.name().toLowerCase()));
    }

    @Test
    @DisplayName("TransactionType.from rejects unknown and null values")
    void rejectsBadTransactionType() {
        assertThrows(IllegalArgumentException.class, () -> TransactionType.from("REFUND"));
        assertThrows(IllegalArgumentException.class, () -> TransactionType.from(null));
    }
}
