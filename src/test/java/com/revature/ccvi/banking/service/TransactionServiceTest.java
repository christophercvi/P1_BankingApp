package com.revature.ccvi.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.revature.ccvi.banking.dao.TransactionFilter;
import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.exception.InsufficientFundsException;
import com.revature.ccvi.banking.exception.InvalidAccountStateException;
import com.revature.ccvi.banking.exception.ResourceNotFoundException;
import com.revature.ccvi.banking.exception.UnauthorizedAccessException;
import com.revature.ccvi.banking.exception.ValidationException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountStatus;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.model.Transaction;
import com.revature.ccvi.banking.model.TransactionType;
import com.revature.ccvi.banking.support.TestFixtures;

@DisplayName("TransactionService: deposits, withdrawals, transfers, and history")
class TransactionServiceTest {

    private TestFixtures fixtures;
    private Customer customer;
    private Account checking;

    @BeforeEach
    void setUp() throws BankingException {
        fixtures = new TestFixtures();
        customer = fixtures.registerCustomer("txowner");
        checking = fixtures.openChecking(customer.getId(), "500.00");
    }

    @Nested
    @DisplayName("US-10 deposits")
    class Deposits {

        @Test
        @DisplayName("credits the account and records the resulting balance")
        void depositIncreasesBalance() throws BankingException {
            Transaction transaction = fixtures.transactionService.deposit(
                    customer.getId(), checking.getId(), new BigDecimal("125.25"), "Paycheck");

            assertEquals(TransactionType.DEPOSIT, transaction.getType());
            assertEquals(new BigDecimal("125.25"), transaction.getAmount());
            assertEquals(new BigDecimal("625.25"), transaction.getResultingBalance());
            assertEquals("Paycheck", transaction.getDescription());
            assertNotNull(transaction.getCreatedAt());
            assertEquals(new BigDecimal("625.25"), fixtures.accountDao.balanceOf(checking.getId()));
        }

        @Test
        @DisplayName("accepts an empty description as no memo")
        void allowsNoDescription() throws BankingException {
            Transaction transaction = fixtures.transactionService.deposit(
                    customer.getId(), checking.getId(), new BigDecimal("1.00"), "   ");

            assertEquals(null, transaction.getDescription());
        }

        @ParameterizedTest(name = "rejects a deposit of {0}")
        @ValueSource(strings = {"0", "-50.00", "0.001", "1000000.01"})
        void rejectsInvalidAmounts(String amount) {
            assertThrows(ValidationException.class, () -> fixtures.transactionService.deposit(
                    customer.getId(), checking.getId(), new BigDecimal(amount), null));
        }

        @Test
        @DisplayName("rejects a null amount and an over-long description")
        void rejectsNullAmountAndLongMemo() {
            assertThrows(ValidationException.class, () -> fixtures.transactionService.deposit(
                    customer.getId(), checking.getId(), null, null));
            assertThrows(ValidationException.class, () -> fixtures.transactionService.deposit(
                    customer.getId(), checking.getId(), new BigDecimal("5.00"), "x".repeat(256)));
        }

        @Test
        @DisplayName("rejects a deposit into an account the customer does not own")
        void rejectsForeignDeposit() throws BankingException {
            Customer stranger = fixtures.registerCustomer("depositor");

            assertThrows(UnauthorizedAccessException.class, () -> fixtures.transactionService.deposit(
                    stranger.getId(), checking.getId(), new BigDecimal("10.00"), null));
        }

        @Test
        @DisplayName("rejects a deposit into a closed account")
        void rejectsClosedAccountDeposit() throws BankingException {
            Account empty = fixtures.openChecking(customer.getId(), "0.00");
            fixtures.accountService.closeAccount(customer.getId(), empty.getId());

            assertThrows(InvalidAccountStateException.class, () -> fixtures.transactionService.deposit(
                    customer.getId(), empty.getId(), new BigDecimal("10.00"), null));
        }

        @Test
        @DisplayName("rejects a deposit into a frozen account")
        void rejectsFrozenAccountDeposit() {
            fixtures.accountDao.updateStatus(checking.getId(), AccountStatus.FROZEN, null);

            InvalidAccountStateException ex = assertThrows(InvalidAccountStateException.class,
                    () -> fixtures.transactionService.deposit(customer.getId(), checking.getId(),
                            new BigDecimal("10.00"), null));
            assertTrue(ex.getMessage().contains("FROZEN"));
        }

        @Test
        @DisplayName("reports a missing account instead of failing silently")
        void rejectsMissingAccount() {
            assertThrows(ResourceNotFoundException.class, () -> fixtures.transactionService.deposit(
                    customer.getId(), "nope", new BigDecimal("10.00"), null));
            assertThrows(ResourceNotFoundException.class, () -> fixtures.transactionService.deposit(
                    customer.getId(), null, new BigDecimal("10.00"), null));
        }
    }

    @Nested
    @DisplayName("US-11 and US-12 withdrawals and overdraft protection")
    class Withdrawals {

        @Test
        @DisplayName("debits the account and records the resulting balance")
        void withdrawDecreasesBalance() throws BankingException {
            Transaction transaction = fixtures.transactionService.withdraw(
                    customer.getId(), checking.getId(), new BigDecimal("200.00"), "Rent");

            assertEquals(TransactionType.WITHDRAWAL, transaction.getType());
            assertEquals(new BigDecimal("300.00"), transaction.getResultingBalance());
            assertEquals(new BigDecimal("300.00"), fixtures.accountDao.balanceOf(checking.getId()));
        }

        @Test
        @DisplayName("allows a withdrawal that empties a checking account exactly")
        void allowsExactBalanceWithdrawal() throws BankingException {
            Transaction transaction = fixtures.transactionService.withdraw(
                    customer.getId(), checking.getId(), new BigDecimal("500.00"), null);

            assertEquals(0, transaction.getResultingBalance().compareTo(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("US-12 refuses to overdraw and leaves the balance untouched")
        void preventsOverdraft() {
            InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                    () -> fixtures.transactionService.withdraw(customer.getId(), checking.getId(),
                            new BigDecimal("500.01"), null));

            assertTrue(ex.getMessage().contains("Insufficient funds"));
            assertEquals(new BigDecimal("500.00"), fixtures.accountDao.balanceOf(checking.getId()));
            assertEquals(1, fixtures.transactionDao.size(), "no ledger entry for a rejected withdrawal");
        }

        @Test
        @DisplayName("enforces the savings minimum balance on withdrawal")
        void enforcesSavingsMinimum() throws BankingException {
            Account savings = fixtures.openSavings(customer.getId(), "100.00");

            InsufficientFundsException ex = assertThrows(InsufficientFundsException.class,
                    () -> fixtures.transactionService.withdraw(customer.getId(), savings.getId(),
                            new BigDecimal("90.00"), null));
            assertTrue(ex.getMessage().contains("minimum balance"));

            Transaction allowed = fixtures.transactionService.withdraw(
                    customer.getId(), savings.getId(), new BigDecimal("75.00"), null);
            assertEquals(new BigDecimal("25.00"), allowed.getResultingBalance());
        }

        @Test
        @DisplayName("rejects invalid withdrawal amounts and unowned accounts")
        void rejectsInvalidWithdrawals() throws BankingException {
            Customer stranger = fixtures.registerCustomer("thief");

            assertThrows(ValidationException.class, () -> fixtures.transactionService.withdraw(
                    customer.getId(), checking.getId(), new BigDecimal("-5.00"), null));
            assertThrows(UnauthorizedAccessException.class, () -> fixtures.transactionService.withdraw(
                    stranger.getId(), checking.getId(), new BigDecimal("5.00"), null));
        }
    }

    @Nested
    @DisplayName("US-13 to US-15 transfers")
    class Transfers {

        @Test
        @DisplayName("moves money between two accounts owned by the same customer")
        void transfersBetweenOwnAccounts() throws BankingException {
            Account savings = fixtures.openSavings(customer.getId(), "100.00");

            TransactionService.TransferReceipt receipt = fixtures.transactionService.transfer(
                    customer.getId(), checking.getId(), savings.getAccountNumber(),
                    new BigDecimal("150.00"), "Move to savings");

            assertEquals(new BigDecimal("350.00"), receipt.sourceBalance());
            assertEquals(new BigDecimal("250.00"), receipt.destinationBalance());
            assertEquals(checking.getAccountNumber(), receipt.sourceAccountNumber());
            assertEquals(savings.getAccountNumber(), receipt.destinationAccountNumber());
            assertNotNull(receipt.completedAt());
        }

        @Test
        @DisplayName("US-14 transfers to an account owned by another customer")
        void transfersToAnotherCustomer() throws BankingException {
            Customer payee = fixtures.registerCustomer("payee");
            Account payeeAccount = fixtures.openChecking(payee.getId(), "0.00");

            TransactionService.TransferReceipt receipt = fixtures.transactionService.transfer(
                    customer.getId(), checking.getId(), payeeAccount.getAccountNumber(),
                    new BigDecimal("75.00"), "Invoice 42");

            assertEquals(new BigDecimal("425.00"), receipt.sourceBalance());
            assertEquals(new BigDecimal("75.00"), fixtures.accountDao.balanceOf(payeeAccount.getId()));
        }

        @Test
        @DisplayName("US-18 writes matching TRANSFER_OUT and TRANSFER_IN ledger entries")
        void writesBothLedgerEntries() throws BankingException {
            Account savings = fixtures.openSavings(customer.getId(), "50.00");
            int before = fixtures.transactionDao.size();

            fixtures.transactionService.transfer(customer.getId(), checking.getId(),
                    savings.getAccountNumber(), new BigDecimal("20.00"), "memo");

            assertEquals(before + 2, fixtures.transactionDao.size());
            List<Transaction> entries = fixtures.transactionDao.all();
            assertTrue(entries.stream().anyMatch(entry -> entry.getType() == TransactionType.TRANSFER_OUT
                    && entry.getAccountId().equals(checking.getId())
                    && entry.getCounterpartyAccountId().equals(savings.getId())));
            assertTrue(entries.stream().anyMatch(entry -> entry.getType() == TransactionType.TRANSFER_IN
                    && entry.getAccountId().equals(savings.getId())
                    && entry.getCounterpartyAccountId().equals(checking.getId())));
        }

        @Test
        @DisplayName("US-15 leaves both balances unchanged when the transfer cannot complete")
        void transferIsAtomic() throws BankingException {
            Account savings = fixtures.openSavings(customer.getId(), "50.00");
            fixtures.transferExecutor.failCredit = true;
            int ledgerBefore = fixtures.transactionDao.size();

            assertThrows(DataAccessException.class, () -> fixtures.transactionService.transfer(
                    customer.getId(), checking.getId(), savings.getAccountNumber(),
                    new BigDecimal("100.00"), null));

            assertEquals(new BigDecimal("500.00"), fixtures.accountDao.balanceOf(checking.getId()));
            assertEquals(new BigDecimal("50.00"), fixtures.accountDao.balanceOf(savings.getId()));
            assertEquals(ledgerBefore, fixtures.transactionDao.size());
        }

        @Test
        @DisplayName("refuses a transfer that would overdraw the source account")
        void refusesUnfundedTransfer() throws BankingException {
            Account savings = fixtures.openSavings(customer.getId(), "50.00");

            assertThrows(InsufficientFundsException.class, () -> fixtures.transactionService.transfer(
                    customer.getId(), checking.getId(), savings.getAccountNumber(),
                    new BigDecimal("600.00"), null));
            assertEquals(new BigDecimal("500.00"), fixtures.accountDao.balanceOf(checking.getId()));
        }

        @Test
        @DisplayName("refuses a transfer to the same account")
        void refusesSelfTransfer() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> fixtures.transactionService.transfer(customer.getId(), checking.getId(),
                            checking.getAccountNumber(), new BigDecimal("10.00"), null));
            assertTrue(ex.getMessage().contains("must be different"));
        }

        @Test
        @DisplayName("refuses a transfer to an unknown or malformed account number")
        void refusesUnknownDestination() {
            assertThrows(ResourceNotFoundException.class, () -> fixtures.transactionService.transfer(
                    customer.getId(), checking.getId(), "1234567890", new BigDecimal("10.00"), null));
            assertThrows(ValidationException.class, () -> fixtures.transactionService.transfer(
                    customer.getId(), checking.getId(), "abc", new BigDecimal("10.00"), null));
        }

        @Test
        @DisplayName("refuses a transfer to a closed account")
        void refusesClosedDestination() throws BankingException {
            Account closed = fixtures.openChecking(customer.getId(), "0.00");
            fixtures.accountService.closeAccount(customer.getId(), closed.getId());

            InvalidAccountStateException ex = assertThrows(InvalidAccountStateException.class,
                    () -> fixtures.transactionService.transfer(customer.getId(), checking.getId(),
                            closed.getAccountNumber(), new BigDecimal("10.00"), null));
            assertTrue(ex.getMessage().contains("CLOSED"));
        }

        @Test
        @DisplayName("refuses a transfer out of an account the customer does not own")
        void refusesForeignSource() throws BankingException {
            Customer stranger = fixtures.registerCustomer("hijacker");
            Account strangerAccount = fixtures.openChecking(stranger.getId(), "0.00");

            assertThrows(UnauthorizedAccessException.class, () -> fixtures.transactionService.transfer(
                    stranger.getId(), checking.getId(), strangerAccount.getAccountNumber(),
                    new BigDecimal("10.00"), null));
        }
    }

    @Nested
    @DisplayName("US-16 to US-18 transaction history")
    class History {

        @Test
        @DisplayName("returns the newest activity first")
        void returnsHistoryNewestFirst() throws BankingException {
            fixtures.transactionService.deposit(customer.getId(), checking.getId(), new BigDecimal("10.00"), "one");
            fixtures.transactionService.withdraw(customer.getId(), checking.getId(), new BigDecimal("5.00"), "two");

            List<Transaction> history = fixtures.transactionService.getHistory(
                    customer.getId(), checking.getId(), null);

            assertEquals(3, history.size(), "opening deposit plus two transactions");
            assertTrue(history.get(0).getCreatedAt().compareTo(history.get(history.size() - 1).getCreatedAt()) >= 0);
        }

        @Test
        @DisplayName("US-18 every entry carries amount, date, type, and resulting balance")
        void entriesAreSelfDescribing() throws BankingException {
            fixtures.transactionService.deposit(customer.getId(), checking.getId(), new BigDecimal("40.00"), "pay");

            for (Transaction transaction : fixtures.transactionService.getHistory(
                    customer.getId(), checking.getId(), null)) {
                assertNotNull(transaction.getAmount());
                assertNotNull(transaction.getCreatedAt());
                assertNotNull(transaction.getType());
                assertNotNull(transaction.getResultingBalance());
            }
        }

        @Test
        @DisplayName("US-17 filters by transaction type")
        void filtersByType() throws BankingException {
            fixtures.transactionService.deposit(customer.getId(), checking.getId(), new BigDecimal("10.00"), null);
            fixtures.transactionService.withdraw(customer.getId(), checking.getId(), new BigDecimal("5.00"), null);

            List<Transaction> withdrawals = fixtures.transactionService.getHistory(customer.getId(),
                    checking.getId(), TransactionFilter.builder().type(TransactionType.WITHDRAWAL).build());

            assertEquals(1, withdrawals.size());
            assertEquals(TransactionType.WITHDRAWAL, withdrawals.get(0).getType());
        }

        @Test
        @DisplayName("US-17 filters by date range")
        void filtersByDateRange() throws BankingException {
            fixtures.transactionService.deposit(customer.getId(), checking.getId(), new BigDecimal("10.00"), null);

            Instant now = Instant.now();
            List<Transaction> insideRange = fixtures.transactionService.getHistory(customer.getId(),
                    checking.getId(), TransactionFilter.builder()
                            .from(now.minus(1, ChronoUnit.HOURS)).to(now.plus(1, ChronoUnit.HOURS)).build());
            List<Transaction> outsideRange = fixtures.transactionService.getHistory(customer.getId(),
                    checking.getId(), TransactionFilter.builder()
                            .from(now.plus(1, ChronoUnit.DAYS)).to(now.plus(2, ChronoUnit.DAYS)).build());

            assertEquals(2, insideRange.size());
            assertTrue(outsideRange.isEmpty());
        }

        @Test
        @DisplayName("honours the row limit")
        void honoursLimit() throws BankingException {
            for (int index = 0; index < 6; index++) {
                fixtures.transactionService.deposit(customer.getId(), checking.getId(),
                        new BigDecimal("1.00"), "batch " + index);
            }

            assertEquals(3, fixtures.transactionService.getHistory(customer.getId(), checking.getId(),
                    TransactionFilter.builder().limit(3).build()).size());
            assertEquals(7, fixtures.transactionService.getHistory(customer.getId(), checking.getId(),
                    TransactionFilter.builder().unlimited().build()).size());
        }

        @Test
        @DisplayName("combines history across every account the customer owns")
        void combinesAcrossAccounts() throws BankingException {
            Account savings = fixtures.openSavings(customer.getId(), "100.00");
            fixtures.transactionService.deposit(customer.getId(), savings.getId(), new BigDecimal("10.00"), null);

            List<Transaction> combined = fixtures.transactionService.getCombinedHistory(
                    customer.getId(), TransactionFilter.builder().unlimited().build());

            assertEquals(3, combined.size());
        }

        @Test
        @DisplayName("returns an empty combined history when the customer has no accounts")
        void emptyCombinedHistory() throws BankingException {
            Customer fresh = fixtures.registerCustomer("nohistory");

            assertTrue(fixtures.transactionService.getCombinedHistory(fresh.getId(), null).isEmpty());
        }

        @Test
        @DisplayName("uses the configured page size when no filter is supplied")
        void appliesDefaultPageSize() throws BankingException {
            BankingRules smallPages = new BankingRules(new BigDecimal("25.00"), BigDecimal.ZERO, 5, 2);
            TestFixtures paged = new TestFixtures(smallPages);
            Customer owner = paged.registerCustomer("pager");
            Account account = paged.openChecking(owner.getId(), "100.00");
            paged.transactionService.deposit(owner.getId(), account.getId(), new BigDecimal("1.00"), null);
            paged.transactionService.deposit(owner.getId(), account.getId(), new BigDecimal("2.00"), null);

            assertEquals(2, paged.transactionService.getHistory(owner.getId(), account.getId(), null).size());
            assertEquals(2, paged.transactionService.getCombinedHistory(owner.getId(), null).size());
        }

        @Test
        @DisplayName("refuses history for an account the customer does not own")
        void refusesForeignHistory() throws BankingException {
            Customer stranger = fixtures.registerCustomer("snoop");

            assertThrows(UnauthorizedAccessException.class, () -> fixtures.transactionService.getHistory(
                    stranger.getId(), checking.getId(), null));
            assertThrows(ResourceNotFoundException.class, () -> fixtures.transactionService.getHistory(
                    customer.getId(), "missing", null));
        }
    }
}
