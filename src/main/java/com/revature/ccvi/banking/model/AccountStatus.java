package com.revature.ccvi.banking.model;

/**
 * Lifecycle state of a bank account. Only ACTIVE accounts may move money.
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED;

    public boolean isTransactable() {
        return this == ACTIVE;
    }

    public static AccountStatus from(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Account status is required");
        }
        return AccountStatus.valueOf(raw.trim().toUpperCase());
    }
}
