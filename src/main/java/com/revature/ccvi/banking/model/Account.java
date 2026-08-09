package com.revature.ccvi.banking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A deposit account owned by exactly one customer. Balances are always stored as
 * BigDecimal with a scale of two to avoid binary floating point rounding drift.
 */
public class Account {

    private String id;
    private String accountNumber;
    private String customerId;
    private AccountType type;
    private AccountStatus status;
    private BigDecimal balance;
    private Instant createdAt;
    private Instant closedAt;

    public Account() {
    }

    public Account(String id, String accountNumber, String customerId, AccountType type,
                   AccountStatus status, BigDecimal balance, Instant createdAt, Instant closedAt) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.type = type;
        this.status = status;
        this.balance = balance;
        this.createdAt = createdAt;
        this.closedAt = closedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public boolean isOwnedBy(String candidateCustomerId) {
        return customerId != null && customerId.equals(candidateCustomerId);
    }

    public boolean isTransactable() {
        return status != null && status.isTransactable();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Account account)) {
            return false;
        }
        return Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Account{number=" + accountNumber + ", type=" + type
                + ", status=" + status + ", balance=" + balance + "}";
    }
}
