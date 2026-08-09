package com.revature.ccvi.banking.service;

import java.math.BigDecimal;

import com.revature.ccvi.banking.config.AppConfig;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Externalized banking policy. Thresholds live in configuration rather than as literals inside
 * services, which keeps the rules auditable and makes them trivially adjustable in tests.
 */
public class BankingRules {

    private final BigDecimal savingsMinimumBalance;
    private final BigDecimal minimumOpeningDeposit;
    private final int maxAccountsPerCustomer;
    private final int defaultHistoryPageSize;

    public BankingRules(AppConfig config) {
        this(config.getDecimal("bank.savings.minimumBalance", "25.00"),
                config.getDecimal("bank.account.minimumOpeningDeposit", "0.00"),
                config.getInt("bank.account.maxPerCustomer", 5),
                config.getInt("bank.history.defaultPageSize", 25));
    }

    public BankingRules(BigDecimal savingsMinimumBalance, BigDecimal minimumOpeningDeposit,
                        int maxAccountsPerCustomer, int defaultHistoryPageSize) {
        this.savingsMinimumBalance = MoneyUtil.normalize(savingsMinimumBalance);
        this.minimumOpeningDeposit = MoneyUtil.normalize(minimumOpeningDeposit);
        this.maxAccountsPerCustomer = maxAccountsPerCustomer;
        this.defaultHistoryPageSize = defaultHistoryPageSize;
    }

    public static BankingRules defaults() {
        return new BankingRules(new BigDecimal("25.00"), BigDecimal.ZERO, 5, 25);
    }

    /** Balance an account of the given type must retain after any debit. */
    public BigDecimal minimumBalanceFor(AccountType type) {
        return type == AccountType.SAVINGS ? savingsMinimumBalance : MoneyUtil.zero();
    }

    public BigDecimal getSavingsMinimumBalance() {
        return savingsMinimumBalance;
    }

    public BigDecimal getMinimumOpeningDeposit() {
        return minimumOpeningDeposit;
    }

    public int getMaxAccountsPerCustomer() {
        return maxAccountsPerCustomer;
    }

    public int getDefaultHistoryPageSize() {
        return defaultHistoryPageSize;
    }
}
