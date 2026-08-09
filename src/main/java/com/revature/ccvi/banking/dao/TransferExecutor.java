package com.revature.ccvi.banking.dao;

import java.math.BigDecimal;

/**
 * Atomic money movement across two accounts (US-15). Debit, credit, and both ledger entries
 * either all persist or none do. PostgreSQL satisfies this with a single JDBC transaction and
 * row locks; MongoDB uses a multi document session transaction.
 */
public interface TransferExecutor {

    /**
     * @param sourceAccountId            account to debit
     * @param destinationAccountId       account to credit
     * @param amount                     positive transfer amount
     * @param minimumSourceBalance       balance the source must retain after the debit
     * @param description                optional memo stored on both ledger entries
     * @return the resulting balances, or {@link TransferOutcome#rejected()} when the source
     *         could not fund the transfer
     */
    TransferOutcome transfer(String sourceAccountId,
                             String destinationAccountId,
                             BigDecimal amount,
                             BigDecimal minimumSourceBalance,
                             String description);

    /** Result of a transfer attempt: either applied with both balances, or rejected. */
    final class TransferOutcome {

        private final boolean applied;
        private final BigDecimal sourceBalance;
        private final BigDecimal destinationBalance;

        private TransferOutcome(boolean applied, BigDecimal sourceBalance, BigDecimal destinationBalance) {
            this.applied = applied;
            this.sourceBalance = sourceBalance;
            this.destinationBalance = destinationBalance;
        }

        public static TransferOutcome applied(BigDecimal sourceBalance, BigDecimal destinationBalance) {
            return new TransferOutcome(true, sourceBalance, destinationBalance);
        }

        public static TransferOutcome rejected() {
            return new TransferOutcome(false, null, null);
        }

        public boolean isApplied() {
            return applied;
        }

        public BigDecimal getSourceBalance() {
            return sourceBalance;
        }

        public BigDecimal getDestinationBalance() {
            return destinationBalance;
        }
    }
}
