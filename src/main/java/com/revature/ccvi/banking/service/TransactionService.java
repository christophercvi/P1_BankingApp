package com.revature.ccvi.banking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.revature.ccvi.banking.dao.AccountDao;
import com.revature.ccvi.banking.dao.TransactionDao;
import com.revature.ccvi.banking.dao.TransactionFilter;
import com.revature.ccvi.banking.dao.TransferExecutor;
import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.exception.InsufficientFundsException;
import com.revature.ccvi.banking.exception.InvalidAccountStateException;
import com.revature.ccvi.banking.exception.ResourceNotFoundException;
import com.revature.ccvi.banking.exception.UnauthorizedAccessException;
import com.revature.ccvi.banking.exception.ValidationException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.Transaction;
import com.revature.ccvi.banking.model.TransactionType;
import com.revature.ccvi.banking.util.InputValidator;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Money movement and history (US-10 through US-18).
 *
 * <p>Balance changes are delegated to guarded DAO operations rather than performed as a read,
 * compute, then write sequence, so two concurrent debits cannot both pass the same balance check
 * and overdraw the account.</p>
 */
public class TransactionService {

    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final TransferExecutor transferExecutor;
    private final BankingRules rules;

    public TransactionService(AccountDao accountDao, TransactionDao transactionDao,
                              TransferExecutor transferExecutor, BankingRules rules) {
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
        this.transferExecutor = transferExecutor;
        this.rules = rules;
    }

    /** US-10: credits an owned, active account and records the ledger entry. */
    public Transaction deposit(String customerId, String accountId, BigDecimal amount, String description)
            throws BankingException {
        BigDecimal validAmount = InputValidator.validateAmount(amount);
        String memo = InputValidator.validateDescription(description);
        Account account = requireTransactableAccount(customerId, accountId);

        BigDecimal newBalance = accountDao.applyBalanceDelta(account.getId(), validAmount, MoneyUtil.zero())
                .orElseThrow(() -> new InvalidAccountStateException(
                        "Deposit could not be applied to account " + account.getAccountNumber() + "."));
        return record(account.getId(), null, TransactionType.DEPOSIT, validAmount, newBalance, memo);
    }

    /** US-11 and US-12: debits an owned, active account without ever allowing an overdraft. */
    public Transaction withdraw(String customerId, String accountId, BigDecimal amount, String description)
            throws BankingException {
        BigDecimal validAmount = InputValidator.validateAmount(amount);
        String memo = InputValidator.validateDescription(description);
        Account account = requireTransactableAccount(customerId, accountId);
        BigDecimal floor = rules.minimumBalanceFor(account.getType());

        Optional<BigDecimal> newBalance =
                accountDao.applyBalanceDelta(account.getId(), validAmount.negate(), floor);
        if (newBalance.isEmpty()) {
            throw insufficientFunds(account, validAmount, floor);
        }
        return record(account.getId(), null, TransactionType.WITHDRAWAL, validAmount, newBalance.get(), memo);
    }

    /**
     * US-13, US-14, and US-15: moves money between two accounts as a single atomic operation.
     * The destination may belong to a different customer, but the source must be owned by the
     * signed-in customer.
     */
    public TransferReceipt transfer(String customerId, String sourceAccountId, String destinationAccountNumber,
                                    BigDecimal amount, String description) throws BankingException {
        BigDecimal validAmount = InputValidator.validateAmount(amount);
        String memo = InputValidator.validateDescription(description);
        String destinationNumber = InputValidator.validateAccountNumber(destinationAccountNumber);

        Account source = requireTransactableAccount(customerId, sourceAccountId);
        Account destination = accountDao.findByAccountNumber(destinationNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No account exists with number " + destinationNumber + "."));
        if (destination.getId().equals(source.getId())) {
            throw new ValidationException("The source and destination accounts must be different.");
        }
        if (!destination.isTransactable()) {
            throw new InvalidAccountStateException("Account " + destinationNumber + " is "
                    + destination.getStatus() + " and cannot receive transfers.");
        }

        BigDecimal floor = rules.minimumBalanceFor(source.getType());
        TransferExecutor.TransferOutcome outcome = transferExecutor.transfer(
                source.getId(), destination.getId(), validAmount, floor, memo);
        if (!outcome.isApplied()) {
            throw insufficientFunds(source, validAmount, floor);
        }
        return new TransferReceipt(source.getAccountNumber(), destination.getAccountNumber(), validAmount,
                outcome.getSourceBalance(), outcome.getDestinationBalance(), Instant.now());
    }

    /** US-16, US-17, and US-18: history for one owned account with optional type and date filters. */
    public List<Transaction> getHistory(String customerId, String accountId, TransactionFilter filter)
            throws ResourceNotFoundException, UnauthorizedAccessException {
        Account account = requireOwnedAccount(customerId, accountId);
        TransactionFilter effective = filter == null
                ? TransactionFilter.builder().limit(rules.getDefaultHistoryPageSize()).build()
                : filter;
        return transactionDao.findByAccountId(account.getId(), effective);
    }

    /** Combined history across every account the customer owns. */
    public List<Transaction> getCombinedHistory(String customerId, TransactionFilter filter) {
        List<String> accountIds = accountDao.findByCustomerId(customerId).stream().map(Account::getId).toList();
        if (accountIds.isEmpty()) {
            return List.of();
        }
        TransactionFilter effective = filter == null
                ? TransactionFilter.builder().limit(rules.getDefaultHistoryPageSize()).build()
                : filter;
        return transactionDao.findByAccountIds(accountIds, effective);
    }

    private Transaction record(String accountId, String counterpartyId, TransactionType type,
                               BigDecimal amount, BigDecimal resultingBalance, String description) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(accountId);
        transaction.setCounterpartyAccountId(counterpartyId);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setResultingBalance(resultingBalance);
        transaction.setDescription(description);
        transaction.setCreatedAt(Instant.now());
        return transactionDao.create(transaction);
    }

    private InsufficientFundsException insufficientFunds(Account account, BigDecimal amount, BigDecimal floor) {
        BigDecimal available = MoneyUtil.normalize(
                account.getBalance() == null ? MoneyUtil.zero() : account.getBalance()).subtract(floor);
        if (available.compareTo(BigDecimal.ZERO) < 0) {
            available = MoneyUtil.zero();
        }
        String suffix = MoneyUtil.isPositive(floor)
                ? " (a minimum balance of " + MoneyUtil.format(floor) + " must remain)"
                : "";
        return new InsufficientFundsException("Insufficient funds: account " + account.getAccountNumber()
                + " has " + MoneyUtil.format(available) + " available but " + MoneyUtil.format(amount)
                + " was requested" + suffix + ".");
    }

    private Account requireOwnedAccount(String customerId, String accountId)
            throws ResourceNotFoundException, UnauthorizedAccessException {
        if (accountId == null || accountId.isBlank()) {
            throw new ResourceNotFoundException("An account must be selected first.");
        }
        Account account = accountDao.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("The requested account does not exist."));
        if (!account.isOwnedBy(customerId)) {
            throw new UnauthorizedAccessException("You are not authorized to access that account.");
        }
        return account;
    }

    private Account requireTransactableAccount(String customerId, String accountId)
            throws ResourceNotFoundException, UnauthorizedAccessException, InvalidAccountStateException {
        Account account = requireOwnedAccount(customerId, accountId);
        if (!account.isTransactable()) {
            throw new InvalidAccountStateException("Account " + account.getAccountNumber() + " is "
                    + account.getStatus() + " and cannot be used for transactions.");
        }
        return account;
    }

    /** Immutable summary returned to the console after a successful transfer. */
    public record TransferReceipt(String sourceAccountNumber, String destinationAccountNumber, BigDecimal amount,
                                  BigDecimal sourceBalance, BigDecimal destinationBalance, Instant completedAt) {
    }
}
