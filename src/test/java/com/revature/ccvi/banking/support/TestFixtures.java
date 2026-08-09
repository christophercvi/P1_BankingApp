package com.revature.ccvi.banking.support;

import java.math.BigDecimal;

import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.service.AccountService;
import com.revature.ccvi.banking.service.AuthService;
import com.revature.ccvi.banking.service.BankingRules;
import com.revature.ccvi.banking.service.TransactionService;

/**
 * Wires the whole service stack over in-memory DAOs. Tests construct one of these per test method
 * so no state leaks between cases.
 */
public class TestFixtures {

    public final InMemoryCustomerDao customerDao = new InMemoryCustomerDao();
    public final InMemoryAccountDao accountDao = new InMemoryAccountDao();
    public final InMemoryTransactionDao transactionDao = new InMemoryTransactionDao();
    public final InMemoryTransferExecutor transferExecutor =
            new InMemoryTransferExecutor(accountDao, transactionDao);
    public final FakePasswordEncoder passwordEncoder = new FakePasswordEncoder();
    public final BankingRules rules;
    public final AuthService authService;
    public final AccountService accountService;
    public final TransactionService transactionService;

    public TestFixtures() {
        this(BankingRules.defaults());
    }

    public TestFixtures(BankingRules rules) {
        this.rules = rules;
        this.authService = new AuthService(customerDao, passwordEncoder);
        this.accountService = new AccountService(accountDao, transactionDao, rules);
        this.transactionService = new TransactionService(accountDao, transactionDao, transferExecutor, rules);
    }

    public Customer registerCustomer(String username) throws BankingException {
        return authService.register(username, "Passw0rd!", username + "@example.com",
                "Test", "Customer", "555-0100");
    }

    public Account openChecking(String customerId, String openingDeposit) throws BankingException {
        return accountService.openAccount(customerId, AccountType.CHECKING, new BigDecimal(openingDeposit));
    }

    public Account openSavings(String customerId, String openingDeposit) throws BankingException {
        return accountService.openAccount(customerId, AccountType.SAVINGS, new BigDecimal(openingDeposit));
    }
}
