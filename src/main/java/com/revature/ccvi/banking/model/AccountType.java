package com.revature.ccvi.banking.model;

/**
 * The kinds of deposit accounts a customer may open.
 */
public enum AccountType {
    CHECKING,
    SAVINGS;

    /**
     * Lenient lookup used when reading values back out of a database column or document field.
     */
    public static AccountType from(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Account type is required");
        }
        return AccountType.valueOf(raw.trim().toUpperCase());
    }
}
