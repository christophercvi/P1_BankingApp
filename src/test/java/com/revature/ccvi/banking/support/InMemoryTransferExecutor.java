package com.revature.ccvi.banking.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.revature.ccvi.banking.dao.TransferExecutor;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Transaction;
import com.revature.ccvi.banking.model.TransactionType;

/**
 * In-memory {@link TransferExecutor} that emulates all-or-nothing semantics: if the credit or the
 * ledger write fails, the debit is undone before the failure propagates. {@link #failCredit} lets
 * a test assert that a partially applied transfer never leaves money missing (US-15).
 */
public class InMemoryTransferExecutor implements TransferExecutor {

    private final InMemoryAccountDao accountDao;
    private final InMemoryTransactionDao transactionDao;

    /** When true the credit leg fails once, exercising the rollback path. */
    public boolean failCredit;

    public InMemoryTransferExecutor(InMemoryAccountDao accountDao, InMemoryTransactionDao transactionDao) {
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
    }

    @Override
    public TransferOutcome transfer(String sourceAccountId, String destinationAccountId, BigDecimal amount,
                                    BigDecimal minimumSourceBalance, String description) {
        Optional<BigDecimal> debited =
                accountDao.applyBalanceDelta(sourceAccountId, amount.negate(), minimumSourceBalance);
        if (debited.isEmpty()) {
            return TransferOutcome.rejected();
        }
        if (failCredit) {
            failCredit = false;
            accountDao.applyBalanceDelta(sourceAccountId, amount, null);
            throw new DataAccessException("Simulated credit failure; the debit was reversed.");
        }
        Optional<BigDecimal> credited = accountDao.applyBalanceDelta(destinationAccountId, amount, null);
        if (credited.isEmpty()) {
            accountDao.applyBalanceDelta(sourceAccountId, amount, null);
            throw new DataAccessException("The destination account no longer exists; the debit was reversed.");
        }
        Instant now = Instant.now();
        transactionDao.create(ledger(sourceAccountId, destinationAccountId, TransactionType.TRANSFER_OUT,
                amount, debited.get(), description, now));
        transactionDao.create(ledger(destinationAccountId, sourceAccountId, TransactionType.TRANSFER_IN,
                amount, credited.get(), description, now));
        return TransferOutcome.applied(debited.get(), credited.get());
    }

    private Transaction ledger(String accountId, String counterpartyId, TransactionType type, BigDecimal amount,
                               BigDecimal resultingBalance, String description, Instant createdAt) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(accountId);
        transaction.setCounterpartyAccountId(counterpartyId);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setResultingBalance(resultingBalance);
        transaction.setDescription(description);
        transaction.setCreatedAt(createdAt);
        return transaction;
    }
}
