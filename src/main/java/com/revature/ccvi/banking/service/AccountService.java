package com.revature.ccvi.banking.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.revature.ccvi.banking.dao.AccountDao;
import com.revature.ccvi.banking.dao.TransactionDao;
import com.revature.ccvi.banking.exception.InvalidAccountStateException;
import com.revature.ccvi.banking.exception.ResourceNotFoundException;
import com.revature.ccvi.banking.exception.UnauthorizedAccessException;
import com.revature.ccvi.banking.exception.ValidationException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountStatus;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.model.Transaction;
import com.revature.ccvi.banking.model.TransactionType;
import com.revature.ccvi.banking.util.InputValidator;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Account lifecycle operations (US-05 through US-09).
 *
 * <p>Ownership is re-checked on every read and write, so a crafted account identifier cannot be
 * used to reach another customer's account (US-23).</p>
 */
public class AccountService {

    private static final int ACCOUNT_NUMBER_LENGTH = 10;
    private static final int ACCOUNT_NUMBER_ATTEMPTS = 25;

    private final AccountDao accountDao;
    private final TransactionDao transactionDao;
    private final BankingRules rules;
    private final SecureRandom random;

    public AccountService(AccountDao accountDao, TransactionDao transactionDao, BankingRules rules) {
        this(accountDao, transactionDao, rules, new SecureRandom());
    }

    AccountService(AccountDao accountDao, TransactionDao transactionDao, BankingRules rules, SecureRandom random) {
        this.accountDao = accountDao;
        this.transactionDao = transactionDao;
        this.rules = rules;
        this.random = random;
    }

    /** US-05 and US-06: opens a checking or savings account with an optional opening deposit. */
    public Account openAccount(String customerId, AccountType type, BigDecimal openingDeposit)
            throws ValidationException, InvalidAccountStateException {
        if (customerId == null || customerId.isBlank()) {
            throw new ValidationException("A customer must be signed in to open an account.");
        }
        if (type == null) {
            throw new ValidationException("Account type must be CHECKING or SAVINGS.");
        }
        BigDecimal deposit = openingDeposit == null ? MoneyUtil.zero() : openingDeposit;
        if (deposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Opening deposit cannot be negative.");
        }
        // Validate before rounding so a value such as 10.005 is rejected rather than silently adjusted.
        deposit = MoneyUtil.isPositive(deposit) ? InputValidator.validateAmount(deposit) : MoneyUtil.zero();
        BigDecimal required = type == AccountType.SAVINGS
                ? rules.getMinimumOpeningDeposit().max(rules.getSavingsMinimumBalance())
                : rules.getMinimumOpeningDeposit();
        if (deposit.compareTo(required) < 0) {
            throw new ValidationException("A " + type.name().toLowerCase() + " account requires an opening deposit of at least "
                    + MoneyUtil.format(required) + ".");
        }

        long openAccounts = accountDao.findByCustomerId(customerId).stream()
                .filter(account -> account.getStatus() != AccountStatus.CLOSED)
                .count();
        if (openAccounts >= rules.getMaxAccountsPerCustomer()) {
            throw new InvalidAccountStateException("You already have the maximum of "
                    + rules.getMaxAccountsPerCustomer() + " open accounts.");
        }

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setCustomerId(customerId);
        account.setType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(deposit);
        account.setCreatedAt(Instant.now());
        Account created = accountDao.create(account);

        if (MoneyUtil.isPositive(deposit)) {
            Transaction opening = new Transaction();
            opening.setAccountId(created.getId());
            opening.setType(TransactionType.DEPOSIT);
            opening.setAmount(deposit);
            opening.setResultingBalance(deposit);
            opening.setDescription("Opening deposit");
            opening.setCreatedAt(Instant.now());
            transactionDao.create(opening);
        }
        return created;
    }

    /** US-07: lists every account belonging to the signed-in customer. */
    public List<Account> listAccounts(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return List.of();
        }
        return accountDao.findByCustomerId(customerId);
    }

    public List<Account> listTransactableAccounts(String customerId) {
        return listAccounts(customerId).stream().filter(Account::isTransactable).toList();
    }

    /** US-08: returns the balance only after confirming ownership. */
    public BigDecimal getBalance(String customerId, String accountId)
            throws ResourceNotFoundException, UnauthorizedAccessException {
        return requireOwnedAccount(customerId, accountId).getBalance();
    }

    public Account getOwnedAccount(String customerId, String accountId)
            throws ResourceNotFoundException, UnauthorizedAccessException {
        return requireOwnedAccount(customerId, accountId);
    }

    public Account getOwnedAccountByNumber(String customerId, String accountNumber)
            throws ValidationException, ResourceNotFoundException, UnauthorizedAccessException {
        String number = InputValidator.validateAccountNumber(accountNumber);
        Account account = accountDao.findByAccountNumber(number)
                .orElseThrow(() -> new ResourceNotFoundException("No account exists with number " + number + "."));
        if (!account.isOwnedBy(customerId)) {
            throw new UnauthorizedAccessException("Account " + number + " does not belong to you.");
        }
        return account;
    }

    /** Used by transfers to resolve a destination that may belong to another customer (US-14). */
    public Account findTransferTarget(String accountNumber)
            throws ValidationException, ResourceNotFoundException, InvalidAccountStateException {
        String number = InputValidator.validateAccountNumber(accountNumber);
        Account account = accountDao.findByAccountNumber(number)
                .orElseThrow(() -> new ResourceNotFoundException("No account exists with number " + number + "."));
        if (!account.isTransactable()) {
            throw new InvalidAccountStateException("Account " + number + " is " + account.getStatus()
                    + " and cannot receive transfers.");
        }
        return account;
    }

    /** US-09: closes an account, which is permitted only when the balance is exactly zero. */
    public Account closeAccount(String customerId, String accountId)
            throws ResourceNotFoundException, UnauthorizedAccessException, InvalidAccountStateException {
        Account account = requireOwnedAccount(customerId, accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidAccountStateException("Account " + account.getAccountNumber() + " is already closed.");
        }
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new InvalidAccountStateException("Frozen accounts must be reinstated by the bank before closing.");
        }
        if (MoneyUtil.isPositive(account.getBalance())) {
            throw new InvalidAccountStateException("Withdraw or transfer the remaining "
                    + MoneyUtil.format(account.getBalance()) + " before closing this account.");
        }
        Instant closedAt = Instant.now();
        if (!accountDao.updateStatus(account.getId(), AccountStatus.CLOSED, closedAt)) {
            throw new ResourceNotFoundException("Account could not be closed because it no longer exists.");
        }
        account.setStatus(AccountStatus.CLOSED);
        account.setClosedAt(closedAt);
        return account;
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

    private String generateAccountNumber() {
        for (int attempt = 0; attempt < ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
            StringBuilder builder = new StringBuilder(ACCOUNT_NUMBER_LENGTH);
            builder.append(random.nextInt(9) + 1);
            for (int digit = 1; digit < ACCOUNT_NUMBER_LENGTH; digit++) {
                builder.append(random.nextInt(10));
            }
            String candidate = builder.toString();
            if (!accountDao.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique account number; please retry.");
    }

    /** Convenience accessor used by the console when it needs the configured policy. */
    public BankingRules getRules() {
        return rules;
    }

    Optional<Account> lookupById(String accountId) {
        return accountDao.findById(accountId);
    }
}
