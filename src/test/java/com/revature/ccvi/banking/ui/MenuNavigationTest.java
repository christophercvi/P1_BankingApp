package com.revature.ccvi.banking.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revature.ccvi.banking.config.DatabaseType;
import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.support.TestFixtures;

/**
 * Drives the console end to end by piping a scripted transcript through {@link MenuRouter}.
 * Each test walks a complete path across several screens rather than probing one screen at a
 * time, so the menus, formatter, and session all run under realistic navigation.
 */
@DisplayName("Console navigation: scripted end-to-end walkthroughs")
class MenuNavigationTest {

    private TestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures();
    }

    /** Feeds the transcript to a fresh router and returns everything the console printed. */
    private String run(String transcript) {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        ConsoleIO console = new ConsoleIO(
                new ByteArrayInputStream(transcript.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(captured, true, StandardCharsets.UTF_8));
        new MenuRouter(console, fixtures.authService, fixtures.accountService,
                fixtures.transactionService, DatabaseType.POSTGRES, "CCVI Community Bank").run();
        return captured.toString(StandardCharsets.UTF_8);
    }

    private static String script(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    @Test
    @DisplayName("registers a customer, logs in, and logs out again")
    void registerLoginLogout() {
        String output = run(script(
                "2",                        // register
                "walkthrough", "Wanda", "Walker", "wanda@example.com", "",
                "Passw0rd1", "Passw0rd1",
                "1",                        // log in
                "walkthrough", "Passw0rd1",
                "5",                        // log out
                "3"));                      // exit

        assertTrue(output.contains("Registration complete for walkthrough"));
        assertTrue(output.contains("Welcome back, Wanda"));
        assertTrue(output.contains("You have been logged out"));
        assertTrue(output.contains("Thank you for banking with CCVI Community Bank"));
        assertTrue(fixtures.customerDao.existsByUsername("walkthrough"));
    }

    @Test
    @DisplayName("rejects registration when the password confirmation does not match")
    void registrationRequiresMatchingConfirmation() {
        String output = run(script(
                "2",
                "mismatch", "Mis", "Match", "mismatch@example.com", "",
                "Passw0rd1", "Different1",
                "3"));

        assertTrue(output.contains("passwords do not match"));
        assertFalse(fixtures.customerDao.existsByUsername("mismatch"));
    }

    @Test
    @DisplayName("reports a failed login without revealing which field was wrong")
    void failedLoginIsGeneric() throws BankingException {
        fixtures.registerCustomer("realuser");

        String output = run(script("1", "realuser", "WrongPass9", "3"));

        assertTrue(output.contains("Invalid username or password."));
    }

    @Test
    @DisplayName("opens a checking account with a deposit and shows it in the account list")
    void opensAccountAndListsIt() throws BankingException {
        Customer customer = fixtures.registerCustomer("opener");

        String output = run(script(
                "1", "opener", "Passw0rd!",
                "1",                        // accounts
                "3", "1", "500.00", "",     // open -> checking -> deposit -> pause
                "1", "",                    // view all accounts -> pause
                "5",                        // back
                "6"));                      // exit

        assertTrue(output.contains("Opened CHECKING account"));
        assertTrue(output.contains("$500.00"));
        assertTrue(output.contains("Total across all accounts: $500.00"));
        assertEquals(1, fixtures.accountService.listAccounts(customer.getId()).size());
    }

    @Test
    @DisplayName("refuses to open a savings account below the minimum balance")
    void savingsMinimumIsEnforcedInTheUi() throws BankingException {
        fixtures.registerCustomer("saver");

        String output = run(script(
                "1", "saver", "Passw0rd!",
                "1",
                "3", "2", "5.00",           // savings with too small a deposit
                "5",
                "6"));

        assertTrue(output.contains("requires an opening deposit of at least $25.00"));
    }

    @Test
    @DisplayName("rejects a non-numeric opening deposit")
    void rejectsNonNumericDeposit() throws BankingException {
        fixtures.registerCustomer("typo");

        String output = run(script(
                "1", "typo", "Passw0rd!",
                "1",
                "3", "1", "one hundred",
                "5",
                "6"));

        assertTrue(output.contains("Opening deposit must be numeric"));
    }

    @Test
    @DisplayName("deposits and withdraws, showing the running balance each time")
    void depositThenWithdraw() throws BankingException {
        Customer customer = fixtures.registerCustomer("teller");
        Account account = fixtures.openChecking(customer.getId(), "100.00");

        String output = run(script(
                "1", "teller", "Passw0rd!",
                "2",                        // transactions
                "1", "1", "250.00", "Payday", "",   // deposit
                "2", "1", "50.00", "Groceries", "", // withdraw
                "6",
                "6"));

        assertTrue(output.contains("Deposited $250.00"));
        assertTrue(output.contains("New balance: $350.00"));
        assertTrue(output.contains("Withdrew $50.00"));
        assertTrue(output.contains("New balance: $300.00"));
        assertEquals(new BigDecimal("300.00"), fixtures.accountDao.balanceOf(account.getId()));
    }

    @Test
    @DisplayName("blocks an overdrawing withdrawal and leaves the balance untouched")
    void overdraftIsBlockedInTheUi() throws BankingException {
        Customer customer = fixtures.registerCustomer("overdrawn");
        Account account = fixtures.openChecking(customer.getId(), "40.00");

        String output = run(script(
                "1", "overdrawn", "Passw0rd!",
                "2",
                "2", "1", "500.00", "",     // withdraw more than the balance
                "6",
                "6"));

        assertTrue(output.contains("Insufficient funds"));
        assertEquals(new BigDecimal("40.00"), fixtures.accountDao.balanceOf(account.getId()));
    }

    @Test
    @DisplayName("transfers to another customer's account by account number")
    void transfersToAnotherCustomer() throws BankingException {
        Customer sender = fixtures.registerCustomer("sender");
        Account source = fixtures.openChecking(sender.getId(), "300.00");
        Customer payee = fixtures.registerCustomer("recipient");
        Account target = fixtures.openChecking(payee.getId(), "0.00");

        String output = run(script(
                "1", "sender", "Passw0rd!",
                "2",
                "3", "1", target.getAccountNumber(), "120.00", "Rent share", "",
                "6",
                "6"));

        assertTrue(output.contains("Transferred $120.00"));
        assertTrue(output.contains("Your new balance: $180.00"));
        assertEquals(new BigDecimal("180.00"), fixtures.accountDao.balanceOf(source.getId()));
        assertEquals(new BigDecimal("120.00"), fixtures.accountDao.balanceOf(target.getId()));
    }

    @Test
    @DisplayName("filters single-account history by type and shows the ledger table")
    void filtersHistoryByType() throws BankingException {
        Customer customer = fixtures.registerCustomer("historian");
        Account account = fixtures.openChecking(customer.getId(), "100.00");
        fixtures.transactionService.deposit(customer.getId(), account.getId(), new BigDecimal("30.00"), "bonus");
        fixtures.transactionService.withdraw(customer.getId(), account.getId(), new BigDecimal("10.00"), "coffee");

        String output = run(script(
                "1", "historian", "Passw0rd!",
                "2",
                "4", "1",                   // single account history -> pick account
                "3", "n",                   // withdrawals only, no date filter
                "",
                "6",
                "6"));

        assertTrue(output.contains("History for account " + account.getAccountNumber()));
        assertTrue(output.contains("WITHDRAWAL"));
        assertTrue(output.contains("Balance After"));
        assertTrue(output.contains("1 transaction(s) shown, newest first."));
        assertFalse(output.contains("bonus"), "deposit rows should be filtered out");
    }

    @Test
    @DisplayName("shows combined history across accounts with a date range filter")
    void combinedHistoryWithDateRange() throws BankingException {
        Customer customer = fixtures.registerCustomer("combiner");
        Account checking = fixtures.openChecking(customer.getId(), "100.00");
        fixtures.openSavings(customer.getId(), "50.00");
        fixtures.transactionService.deposit(customer.getId(), checking.getId(), new BigDecimal("5.00"), null);

        String today = java.time.LocalDate.now().toString();
        String output = run(script(
                "1", "combiner", "Passw0rd!",
                "2",
                "5",                        // combined history
                "1", "y", today, today,     // all types, today only
                "",
                "6",
                "6"));

        assertTrue(output.contains("History across all of your accounts"));
        assertTrue(output.contains("3 transaction(s) shown, newest first."));
    }

    @Test
    @DisplayName("rejects an inverted date range in the history filter")
    void rejectsInvertedDateRange() throws BankingException {
        Customer customer = fixtures.registerCustomer("baddates");
        fixtures.openChecking(customer.getId(), "10.00");

        String output = run(script(
                "1", "baddates", "Passw0rd!",
                "2",
                "5",
                "1", "y", "2026-06-01", "2026-01-01",
                "6",
                "6"));

        assertTrue(output.contains("start date must not be after the end date"));
    }

    @Test
    @DisplayName("closes an emptied account after confirmation and refuses a funded one")
    void closesAccountAfterConfirmation() throws BankingException {
        Customer customer = fixtures.registerCustomer("closer");
        Account funded = fixtures.openChecking(customer.getId(), "75.00");

        String output = run(script(
                "1", "closer", "Passw0rd!",
                "1",                        // accounts
                "4", "1", "y",              // try to close while still funded
                "5",                        // back to main menu
                "2",                        // transactions
                "2", "1", "75.00", "", "",  // withdraw everything: amount, memo, pause
                "6",                        // back to main menu
                "1",                        // accounts
                "4", "1", "y", "",          // close it for real
                "5",
                "6"));

        assertTrue(output.contains("Withdraw or transfer the remaining $75.00 before closing this account."),
                "expected the funded-close attempt to be refused");
        assertTrue(output.contains("is now closed"));
        assertTrue(fixtures.accountService.listTransactableAccounts(customer.getId()).isEmpty());
    }

    @Test
    @DisplayName("cancels a close when the customer declines the confirmation")
    void cancelsCloseOnDecline() throws BankingException {
        Customer customer = fixtures.registerCustomer("cautious");
        Account account = fixtures.openChecking(customer.getId(), "0.00");

        String output = run(script(
                "1", "cautious", "Passw0rd!",
                "1",
                "4", "1", "n",
                "5",
                "6"));

        assertTrue(output.contains("Cancelled."));
        assertEquals(1, fixtures.accountService.listTransactableAccounts(customer.getId()).size());
        assertTrue(fixtures.accountService.listAccounts(customer.getId()).get(0).isTransactable());
        assertEquals(account.getId(), fixtures.accountService.listAccounts(customer.getId()).get(0).getId());
    }

    @Test
    @DisplayName("views a single account balance through the picker")
    void viewsSingleBalance() throws BankingException {
        Customer customer = fixtures.registerCustomer("peeker");
        Account account = fixtures.openSavings(customer.getId(), "425.75");

        String output = run(script(
                "1", "peeker", "Passw0rd!",
                "1",
                "2", "1", "",
                "5",
                "6"));

        assertTrue(output.contains("Account " + account.getAccountNumber() + " (SAVINGS)"));
        assertTrue(output.contains("Balance: $425.75"));
        assertTrue(output.contains("Status:  ACTIVE"));
    }

    @Test
    @DisplayName("prompts to open an account when the customer has none")
    void promptsWhenNoAccounts() throws BankingException {
        fixtures.registerCustomer("empty");

        String output = run(script(
                "1", "empty", "Passw0rd!",
                "2",
                "1",                        // deposit with no accounts
                "6",
                "4", "",                    // dashboard
                "6"));

        assertTrue(output.contains("no active accounts available for transactions"));
        assertTrue(output.contains("You do not have any accounts yet"));
    }

    @Test
    @DisplayName("views and updates the profile, keeping values on empty input")
    void viewsAndUpdatesProfile() throws BankingException {
        Customer customer = fixtures.registerCustomer("profiler");

        String output = run(script(
                "1", "profiler", "Passw0rd!",
                "3",
                "1", "",                                        // view profile
                "2", "new@example.com", "Nina", "", "555-7777", "",  // update, keep last name
                "1", "",                                        // view again
                "4",
                "6"));

        assertTrue(output.contains("Username:   profiler"));
        assertTrue(output.contains("Profile updated."));
        assertTrue(output.contains("new@example.com"));
        assertEquals("Nina", fixtures.authService.findById(customer.getId()).getFirstName());
        assertEquals("Customer", fixtures.authService.findById(customer.getId()).getLastName());
    }

    @Test
    @DisplayName("changes the password and accepts the new one on the next login")
    void changesPassword() throws BankingException {
        fixtures.registerCustomer("rotator");

        String output = run(script(
                "1", "rotator", "Passw0rd!",
                "3",
                "3", "Passw0rd!", "Rotated123", "Rotated123", "",
                "4",
                "5",                        // log out
                "1", "rotator", "Rotated123",
                "6"));

        assertTrue(output.contains("Password changed."));
        assertTrue(output.contains("Welcome back, Test"));
    }

    @Test
    @DisplayName("rejects a password change when the confirmation does not match")
    void rejectsMismatchedPasswordChange() throws BankingException {
        fixtures.registerCustomer("mismatcher");

        String output = run(script(
                "1", "mismatcher", "Passw0rd!",
                "3",
                "3", "Passw0rd!", "Rotated123", "Different123",
                "4",
                "6"));

        assertTrue(output.contains("new passwords do not match"));
    }

    @Test
    @DisplayName("renders the dashboard with a total balance and recent activity")
    void rendersDashboard() throws BankingException {
        Customer customer = fixtures.registerCustomer("dash");
        Account checking = fixtures.openChecking(customer.getId(), "200.00");
        fixtures.openSavings(customer.getId(), "300.00");
        fixtures.transactionService.deposit(customer.getId(), checking.getId(), new BigDecimal("25.00"), "extra");

        String output = run(script(
                "1", "dash", "Passw0rd!",
                "4", "",
                "6"));

        assertTrue(output.contains("Dashboard"));
        assertTrue(output.contains("Total balance: $525.00"));
        assertTrue(output.contains("Recent activity"));
        assertTrue(output.contains("DEPOSIT"));
    }

    @Test
    @DisplayName("re-prompts after an out-of-range menu choice instead of exiting")
    void invalidMenuChoiceIsRecoverable() {
        String output = run(script("9", "abc", "3"));

        assertTrue(output.contains("Please choose an option between 1 and 3"));
        assertTrue(output.contains("'abc' is not a valid menu option"));
        assertTrue(output.contains("Thank you for banking"));
    }

    @Test
    @DisplayName("exits cleanly when input ends unexpectedly")
    void exitsOnEndOfInput() {
        String output = run("1\n");

        assertTrue(output.contains("Thank you for banking"));
    }
}
