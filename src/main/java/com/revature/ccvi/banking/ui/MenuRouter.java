package com.revature.ccvi.banking.ui;

import java.math.BigDecimal;
import java.util.List;

import com.revature.ccvi.banking.config.DatabaseType;
import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.exception.EndOfInputException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.service.AccountService;
import com.revature.ccvi.banking.service.AuthService;
import com.revature.ccvi.banking.service.TransactionService;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Top level console navigation. Every menu action is wrapped so a business failure or a database
 * error is reported and the loop continues rather than terminating the application (US-22).
 */
public class MenuRouter {

    private final ConsoleIO console;
    private final AuthService authService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final DatabaseType databaseType;
    private final String bankName;

    private final Session session = new Session();
    private final AccountMenu accountMenu;
    private final TransactionMenu transactionMenu;
    private final ProfileMenu profileMenu;

    public MenuRouter(ConsoleIO console, AuthService authService, AccountService accountService,
                      TransactionService transactionService, DatabaseType databaseType, String bankName) {
        this.console = console;
        this.authService = authService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.databaseType = databaseType;
        this.bankName = bankName;
        this.accountMenu = new AccountMenu(console, accountService, session);
        this.transactionMenu = new TransactionMenu(console, transactionService, accountMenu, session);
        this.profileMenu = new ProfileMenu(console, authService, session);
    }

    public void run() {
        console.heading(bankName);
        console.info("Console banking. Persistence backend: " + databaseType + ".");
        boolean running = true;
        while (running) {
            try {
                running = session.isAuthenticated() ? showMainMenu() : showWelcomeMenu();
            } catch (EndOfInputException ex) {
                // stdin closed (piped script, redirected input): shut down instead of re-prompting
                running = false;
            }
        }
        console.blank();
        console.println("Thank you for banking with " + bankName + ". Goodbye.");
    }

    private boolean showWelcomeMenu() throws EndOfInputException {
        console.heading("Welcome");
        console.println("  1) Log in");
        console.println("  2) Register a new customer");
        console.println("  3) Exit");
        try {
            switch (console.readMenuChoice("\nSelect an option [1-3]: ", 1, 3)) {
                case 1 -> login();
                case 2 -> register();
                default -> {
                    return false;
                }
            }
        } catch (EndOfInputException ex) {
            throw ex;
        } catch (BankingException ex) {
            console.error(ex.getMessage());
        } catch (DataAccessException ex) {
            console.error("Database error: " + ex.getMessage());
        }
        return true;
    }

    private boolean showMainMenu() throws EndOfInputException {
        Customer customer = session.getCustomer();
        console.heading("Main Menu - " + customer.getFullName().trim());
        console.println("  1) Accounts");
        console.println("  2) Transactions");
        console.println("  3) Profile");
        console.println("  4) Dashboard summary");
        console.println("  5) Log out");
        console.println("  6) Exit");
        try {
            switch (console.readMenuChoice("\nSelect an option [1-6]: ", 1, 6)) {
                case 1 -> accountMenu.show();
                case 2 -> transactionMenu.show();
                case 3 -> profileMenu.show();
                case 4 -> dashboard();
                case 5 -> {
                    session.logout();
                    console.success("You have been logged out.");
                }
                default -> {
                    return false;
                }
            }
        } catch (EndOfInputException ex) {
            throw ex;
        } catch (BankingException ex) {
            console.error(ex.getMessage());
        } catch (DataAccessException ex) {
            console.error("Database error: " + ex.getMessage());
        }
        return true;
    }

    private void login() throws BankingException {
        console.heading("Log In");
        String username = console.readRequired("Username: ", "Username");
        String password = console.readPassword("Password: ");
        Customer customer = authService.login(username, password);
        session.login(customer);
        console.success("Welcome back, " + customer.getFirstName() + ".");
    }

    private void register() throws BankingException {
        console.heading("Register");
        console.info("Usernames are 3-30 characters of letters, digits, or underscores.");
        String username = console.readRequired("Username: ", "Username");
        String firstName = console.readRequired("First name: ", "First name");
        String lastName = console.readRequired("Last name: ", "Last name");
        String email = console.readRequired("Email: ", "Email");
        String phone = console.readOptional("Phone (optional): ");
        String password = console.readPassword("Password (min 8 chars, letters and digits): ");
        String confirmation = console.readPassword("Confirm password: ");
        if (!password.equals(confirmation)) {
            console.error("The passwords do not match. Registration cancelled.");
            return;
        }
        Customer customer = authService.register(username, password, email, firstName, lastName, phone);
        console.success("Registration complete for " + customer.getUsername() + ". You may now log in.");
    }

    private void dashboard() {
        console.heading("Dashboard");
        List<Account> accounts = accountService.listAccounts(session.getCustomerId());
        ConsoleFormatter.printAccounts(console, accounts);
        if (!accounts.isEmpty()) {
            BigDecimal total = accounts.stream().map(Account::getBalance)
                    .reduce(MoneyUtil.zero(), BigDecimal::add);
            console.info("Total balance: " + MoneyUtil.format(total));
        }
        console.blank();
        console.println("  Recent activity");
        ConsoleFormatter.printTransactions(console, transactionService.getCombinedHistory(
                session.getCustomerId(), com.revature.ccvi.banking.dao.TransactionFilter.builder().limit(5).build()));
        console.pause();
    }
}
