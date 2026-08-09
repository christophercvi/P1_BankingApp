package com.revature.ccvi.banking.model;

/**
 * Ledger entry classification. A transfer produces one TRANSFER_OUT entry on the
 * source account and one TRANSFER_IN entry on the destination account.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_IN,
    TRANSFER_OUT;

    public boolean isCredit() {
        return this == DEPOSIT || this == TRANSFER_IN;
    }

    public static TransactionType from(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        return TransactionType.valueOf(raw.trim().toUpperCase());
    }
}
