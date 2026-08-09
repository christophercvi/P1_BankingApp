package com.revature.ccvi.banking.ui;

import java.math.BigDecimal;
import java.util.List;

import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.exception.EndOfInputException;
import com.revature.ccvi.banking.exception.ValidationException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.service.AccountService;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Account management screens (US-05 through US-09).
 */
class AccountMenu {

    private final ConsoleIO console;
    private final AccountService accountService;
    private final Session session;

    AccountMenu(ConsoleIO console, AccountService accountService, Session session) {
        this.console = console;
        this.accountService = accountService;
        this.session = session;
    }

    void show() throws EndOfInputException {
        boolean running = true;
        while (running) {
            console.heading("Account Management");
            console.println("  1) View all accounts");
            console.println("  2) View a single account balance");
            console.println("  3) Open a new account");
            console.println("  4) Close an account");
            console.println("  5) Back to main menu");
            try {
                switch (console.readMenuChoice("\nSelect an option [1-5]: ", 1, 5)) {
                    case 1 -> viewAccounts();
                    case 2 -> viewBalance();
                    case 3 -> openAccount();
                    case 4 -> closeAccount();
                    default -> running = false;
                }
            } catch (EndOfInputException ex) {
                throw ex;
            } catch (BankingException ex) {
                console.error(ex.getMessage());
            } catch (DataAccessException ex) {
                console.error("Database error: " + ex.getMessage());
            }
        }
    }

    private void viewAccounts() {
        console.heading("Your Accounts");
        List<Account> accounts = accountService.listAccounts(session.getCustomerId());
        ConsoleFormatter.printAccounts(console, accounts);
        if (!accounts.isEmpty()) {
            BigDecimal total = accounts.stream()
                    .map(Account::getBalance)
                    .reduce(MoneyUtil.zero(), BigDecimal::add);
            console.info("Total across all accounts: " + MoneyUtil.format(total));
        }
        console.pause();
    }

    private void viewBalance() throws BankingException {
        Account account = selectAccount("View balance for which account?");
        if (account == null) {
            return;
        }
        console.blank();
        console.success("Account " + account.getAccountNumber() + " (" + account.getType() + ")");
        console.info("Status:  " + account.getStatus());
        console.info("Balance: " + MoneyUtil.format(
                accountService.getBalance(session.getCustomerId(), account.getId())));
        console.pause();
    }

    private void openAccount() throws BankingException {
        console.heading("Open a New Account");
        console.println("  1) Checking");
        console.println("  2) Savings (minimum balance "
                + MoneyUtil.format(accountService.getRules().getSavingsMinimumBalance()) + ")");
        console.println("  3) Cancel");
        int choice = console.readMenuChoice("\nAccount type [1-3]: ", 1, 3);
        if (choice == 3) {
            return;
        }
        AccountType type = choice == 1 ? AccountType.CHECKING : AccountType.SAVINGS;
        String rawDeposit = console.readOptional("Opening deposit (press Enter for 0.00): ");
        BigDecimal deposit = MoneyUtil.zero();
        if (rawDeposit != null) {
            try {
                deposit = new BigDecimal(rawDeposit.replace(",", "").replace("$", ""));
            } catch (NumberFormatException ex) {
                throw new ValidationException("Opening deposit must be numeric, for example 100.00.");
            }
        }
        Account created = accountService.openAccount(session.getCustomerId(), type, deposit);
        console.blank();
        console.success("Opened " + created.getType() + " account " + created.getAccountNumber()
                + " with a balance of " + MoneyUtil.format(created.getBalance()) + ".");
        console.pause();
    }

    private void closeAccount() throws BankingException {
        Account account = selectAccount("Close which account?");
        if (account == null) {
            return;
        }
        console.info("Account " + account.getAccountNumber() + " currently holds "
                + MoneyUtil.format(account.getBalance()) + ".");
        if (!console.readYesNo("Closing an account cannot be undone. Continue? (y/N): ")) {
            console.info("Cancelled.");
            return;
        }
        Account closed = accountService.closeAccount(session.getCustomerId(), account.getId());
        console.success("Account " + closed.getAccountNumber() + " is now closed.");
        console.pause();
    }

    /** Shared account picker; returns null when the customer has no accounts or cancels. */
    Account selectAccount(String prompt) throws ValidationException, EndOfInputException {
        List<Account> accounts = accountService.listAccounts(session.getCustomerId());
        if (accounts.isEmpty()) {
            console.info("You do not have any accounts yet.");
            return null;
        }
        console.heading(prompt);
        ConsoleFormatter.printAccounts(console, accounts);
        console.println("  " + (accounts.size() + 1) + ") Cancel");
        int choice = console.readMenuChoice("\nSelect an account [1-" + (accounts.size() + 1) + "]: ",
                1, accounts.size() + 1);
        return choice == accounts.size() + 1 ? null : accounts.get(choice - 1);
    }

    /** Picker limited to accounts that can currently transact. */
    Account selectTransactableAccount(String prompt) throws ValidationException, EndOfInputException {
        List<Account> accounts = accountService.listTransactableAccounts(session.getCustomerId());
        if (accounts.isEmpty()) {
            console.info("You have no active accounts available for transactions.");
            return null;
        }
        console.heading(prompt);
        ConsoleFormatter.printAccounts(console, accounts);
        console.println("  " + (accounts.size() + 1) + ") Cancel");
        int choice = console.readMenuChoice("\nSelect an account [1-" + (accounts.size() + 1) + "]: ",
                1, accounts.size() + 1);
        return choice == accounts.size() + 1 ? null : accounts.get(choice - 1);
    }
}
