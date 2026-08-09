package com.revature.ccvi.banking.dao.mongo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountStatus;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.model.Transaction;
import com.revature.ccvi.banking.model.TransactionType;

@DisplayName("MongoDocumentMapper: BSON conversions")
class MongoDocumentMapperTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Test
    @DisplayName("round-trips a customer through a document")
    void roundTripsCustomer() {
        Customer original = new Customer("cust-1", "jdoe", "j@example.com", "Jane", "Doe",
                "555-0000", "$argon2id$hash", NOW, NOW);

        Document document = MongoDocumentMapper.toDocument(original);
        Customer restored = MongoDocumentMapper.toCustomer(document);

        assertEquals("cust-1", document.getString("_id"));
        assertEquals(original.getUsername(), restored.getUsername());
        assertEquals(original.getEmail(), restored.getEmail());
        assertEquals(original.getFirstName(), restored.getFirstName());
        assertEquals(original.getLastName(), restored.getLastName());
        assertEquals(original.getPhone(), restored.getPhone());
        assertEquals(original.getPasswordHash(), restored.getPasswordHash());
        assertEquals(NOW, restored.getCreatedAt());
        assertEquals(NOW, restored.getLastLoginAt());
    }

    @Test
    @DisplayName("round-trips an account and stores the balance as Decimal128")
    void roundTripsAccount() {
        Account original = new Account("acct-1", "1234567890", "cust-1", AccountType.SAVINGS,
                AccountStatus.FROZEN, new BigDecimal("987.65"), NOW, NOW);

        Document document = MongoDocumentMapper.toDocument(original);
        Account restored = MongoDocumentMapper.toAccount(document);

        assertTrue(document.get("balance") instanceof Decimal128);
        assertEquals("SAVINGS", document.getString("accountType"));
        assertEquals("FROZEN", document.getString("status"));
        assertEquals(original.getAccountNumber(), restored.getAccountNumber());
        assertEquals(original.getCustomerId(), restored.getCustomerId());
        assertEquals(AccountType.SAVINGS, restored.getType());
        assertEquals(AccountStatus.FROZEN, restored.getStatus());
        assertEquals(new BigDecimal("987.65"), restored.getBalance());
        assertEquals(NOW, restored.getCreatedAt());
        assertEquals(NOW, restored.getClosedAt());
    }

    @Test
    @DisplayName("round-trips a transaction including the counterparty reference")
    void roundTripsTransaction() {
        Transaction original = new Transaction("tx-1", "acct-1", "acct-2", TransactionType.TRANSFER_OUT,
                new BigDecimal("50.00"), new BigDecimal("150.00"), "memo", NOW);

        Document document = MongoDocumentMapper.toDocument(original);
        Transaction restored = MongoDocumentMapper.toTransaction(document);

        assertEquals("TRANSFER_OUT", document.getString("transactionType"));
        assertEquals("acct-2", restored.getCounterpartyAccountId());
        assertEquals(new BigDecimal("50.00"), restored.getAmount());
        assertEquals(new BigDecimal("150.00"), restored.getResultingBalance());
        assertEquals("memo", restored.getDescription());
        assertEquals(NOW, restored.getCreatedAt());
    }

    @Test
    @DisplayName("maps null documents to null objects")
    void handlesNullDocuments() {
        assertNull(MongoDocumentMapper.toCustomer(null));
        assertNull(MongoDocumentMapper.toAccount(null));
        assertNull(MongoDocumentMapper.toTransaction(null));
    }

    @Test
    @DisplayName("preserves absent optional fields as null")
    void preservesAbsentFields() {
        Customer customer = new Customer("cust-2", "nophone", "n@example.com", "No", "Phone",
                null, "hash", NOW, null);
        Account account = new Account("acct-2", "9999999999", "cust-2", AccountType.CHECKING,
                AccountStatus.ACTIVE, BigDecimal.ZERO, NOW, null);
        Transaction transaction = new Transaction("tx-2", "acct-2", null, TransactionType.DEPOSIT,
                BigDecimal.ONE, BigDecimal.ONE, null, NOW);

        assertNull(MongoDocumentMapper.toCustomer(MongoDocumentMapper.toDocument(customer)).getPhone());
        assertNull(MongoDocumentMapper.toCustomer(MongoDocumentMapper.toDocument(customer)).getLastLoginAt());
        assertNull(MongoDocumentMapper.toAccount(MongoDocumentMapper.toDocument(account)).getClosedAt());
        assertNull(MongoDocumentMapper.toTransaction(
                MongoDocumentMapper.toDocument(transaction)).getCounterpartyAccountId());
        assertNull(MongoDocumentMapper.toTransaction(
                MongoDocumentMapper.toDocument(transaction)).getDescription());
    }

    @Test
    @DisplayName("normalizes decimal values to a scale of two")
    void normalizesDecimals() {
        assertEquals(new BigDecimal("10.00"), MongoDocumentMapper.toDecimal(new BigDecimal("10")).bigDecimalValue());
        assertNull(MongoDocumentMapper.toDecimal(null));
        assertNull(MongoDocumentMapper.toBigDecimal(null));
        assertEquals(new BigDecimal("5.25"), MongoDocumentMapper.toBigDecimal(new Decimal128(new BigDecimal("5.25"))));
        assertEquals(new BigDecimal("7.50"), MongoDocumentMapper.toBigDecimal(new BigDecimal("7.5")));
        assertEquals(new BigDecimal("3.00"), MongoDocumentMapper.toBigDecimal(3));
        assertEquals(new BigDecimal("2.50"), MongoDocumentMapper.toBigDecimal(2.5d));
        assertEquals(new BigDecimal("8.75"), MongoDocumentMapper.toBigDecimal("8.75"));
    }

    @Test
    @DisplayName("converts between Instant and Date, tolerating nulls")
    void convertsTimestamps() {
        Date date = MongoDocumentMapper.toDate(NOW);

        assertNotNull(date);
        assertEquals(NOW, MongoDocumentMapper.toInstant(date));
        assertNull(MongoDocumentMapper.toDate(null));
        assertNull(MongoDocumentMapper.toInstant(null));
    }
}
