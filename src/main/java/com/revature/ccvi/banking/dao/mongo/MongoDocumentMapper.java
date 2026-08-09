package com.revature.ccvi.banking.dao.mongo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;

import org.bson.Document;
import org.bson.types.Decimal128;

import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountStatus;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.model.Transaction;
import com.revature.ccvi.banking.model.TransactionType;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Converts between domain objects and BSON documents. Monetary fields use Decimal128 so the
 * MongoDB representation keeps exact decimal semantics, matching NUMERIC(19,2) in PostgreSQL.
 */
public final class MongoDocumentMapper {

    private MongoDocumentMapper() {
    }

    public static Document toDocument(Customer customer) {
        return new Document("_id", customer.getId())
                .append("username", customer.getUsername())
                .append("email", customer.getEmail())
                .append("firstName", customer.getFirstName())
                .append("lastName", customer.getLastName())
                .append("phone", customer.getPhone())
                .append("passwordHash", customer.getPasswordHash())
                .append("createdAt", toDate(customer.getCreatedAt()))
                .append("lastLoginAt", toDate(customer.getLastLoginAt()));
    }

    public static Customer toCustomer(Document document) {
        if (document == null) {
            return null;
        }
        Customer customer = new Customer();
        customer.setId(document.getString("_id"));
        customer.setUsername(document.getString("username"));
        customer.setEmail(document.getString("email"));
        customer.setFirstName(document.getString("firstName"));
        customer.setLastName(document.getString("lastName"));
        customer.setPhone(document.getString("phone"));
        customer.setPasswordHash(document.getString("passwordHash"));
        customer.setCreatedAt(toInstant(document.getDate("createdAt")));
        customer.setLastLoginAt(toInstant(document.getDate("lastLoginAt")));
        return customer;
    }

    public static Document toDocument(Account account) {
        return new Document("_id", account.getId())
                .append("accountNumber", account.getAccountNumber())
                .append("customerId", account.getCustomerId())
                .append("accountType", account.getType().name())
                .append("status", account.getStatus().name())
                .append("balance", toDecimal(account.getBalance()))
                .append("createdAt", toDate(account.getCreatedAt()))
                .append("closedAt", toDate(account.getClosedAt()));
    }

    public static Account toAccount(Document document) {
        if (document == null) {
            return null;
        }
        Account account = new Account();
        account.setId(document.getString("_id"));
        account.setAccountNumber(document.getString("accountNumber"));
        account.setCustomerId(document.getString("customerId"));
        account.setType(AccountType.from(document.getString("accountType")));
        account.setStatus(AccountStatus.from(document.getString("status")));
        account.setBalance(toBigDecimal(document.get("balance")));
        account.setCreatedAt(toInstant(document.getDate("createdAt")));
        account.setClosedAt(toInstant(document.getDate("closedAt")));
        return account;
    }

    public static Document toDocument(Transaction transaction) {
        return new Document("_id", transaction.getId())
                .append("accountId", transaction.getAccountId())
                .append("counterpartyAccountId", transaction.getCounterpartyAccountId())
                .append("transactionType", transaction.getType().name())
                .append("amount", toDecimal(transaction.getAmount()))
                .append("resultingBalance", toDecimal(transaction.getResultingBalance()))
                .append("description", transaction.getDescription())
                .append("createdAt", toDate(transaction.getCreatedAt()));
    }

    public static Transaction toTransaction(Document document) {
        if (document == null) {
            return null;
        }
        Transaction transaction = new Transaction();
        transaction.setId(document.getString("_id"));
        transaction.setAccountId(document.getString("accountId"));
        transaction.setCounterpartyAccountId(document.getString("counterpartyAccountId"));
        transaction.setType(TransactionType.from(document.getString("transactionType")));
        transaction.setAmount(toBigDecimal(document.get("amount")));
        transaction.setResultingBalance(toBigDecimal(document.get("resultingBalance")));
        transaction.setDescription(document.getString("description"));
        transaction.setCreatedAt(toInstant(document.getDate("createdAt")));
        return transaction;
    }

    public static Decimal128 toDecimal(BigDecimal value) {
        return value == null ? null : new Decimal128(MoneyUtil.normalize(value));
    }

    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Decimal128 decimal) {
            return MoneyUtil.normalize(decimal.bigDecimalValue());
        }
        if (value instanceof BigDecimal decimal) {
            return MoneyUtil.normalize(decimal);
        }
        if (value instanceof Number number) {
            return MoneyUtil.normalize(BigDecimal.valueOf(number.doubleValue()));
        }
        return MoneyUtil.normalize(new BigDecimal(value.toString()));
    }

    public static Date toDate(Instant instant) {
        return instant == null ? null : Date.from(instant);
    }

    public static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
