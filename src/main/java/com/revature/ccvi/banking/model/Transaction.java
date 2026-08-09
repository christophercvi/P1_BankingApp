package com.revature.ccvi.banking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * An immutable ledger entry. Every balance-changing operation appends one of these and
 * records the balance that resulted, so history can be replayed without recomputation.
 */
public class Transaction {

    private String id;
    private String accountId;
    private String counterpartyAccountId;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal resultingBalance;
    private String description;
    private Instant createdAt;

    public Transaction() {
    }

    public Transaction(String id, String accountId, String counterpartyAccountId, TransactionType type,
                       BigDecimal amount, BigDecimal resultingBalance, String description, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.counterpartyAccountId = counterpartyAccountId;
        this.type = type;
        this.amount = amount;
        this.resultingBalance = resultingBalance;
        this.description = description;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getCounterpartyAccountId() {
        return counterpartyAccountId;
    }

    public void setCounterpartyAccountId(String counterpartyAccountId) {
        this.counterpartyAccountId = counterpartyAccountId;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getResultingBalance() {
        return resultingBalance;
    }

    public void setResultingBalance(BigDecimal resultingBalance) {
        this.resultingBalance = resultingBalance;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** Signed amount from the perspective of the owning account. */
    public BigDecimal getSignedAmount() {
        if (amount == null || type == null) {
            return BigDecimal.ZERO;
        }
        return type.isCredit() ? amount : amount.negate();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Transaction transaction)) {
            return false;
        }
        return Objects.equals(id, transaction.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Transaction{type=" + type + ", amount=" + amount
                + ", resultingBalance=" + resultingBalance + ", createdAt=" + createdAt + "}";
    }
}
