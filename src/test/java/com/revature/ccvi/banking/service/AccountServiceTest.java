package com.revature.ccvi.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.exception.InvalidAccountStateException;
import com.revature.ccvi.banking.exception.ResourceNotFoundException;
import com.revature.ccvi.banking.exception.UnauthorizedAccessException;
import com.revature.ccvi.banking.exception.ValidationException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountStatus;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.support.TestFixtures;

@DisplayName("AccountService: opening, listing, balances, and closure rules")
class AccountServiceTest {

    private TestFixtures fixtures;
    private Customer customer;

    @BeforeEach
    void setUp() throws BankingException {
        fixtures = new TestFixtures();
        customer = fixtures.registerCustomer("accountowner");
    }

    @Nested
    @DisplayName("US-05 and US-06 opening accounts")
    class Opening {

        @ParameterizedTest(name = "opens a {0} account")
        @EnumSource(AccountType.class)
        void opensEachAccountType(AccountType type) throws BankingException {
            Account account = fixtures.accountService.openAccount(customer.getId(), type, new BigDecimal("100.00"));

            assertNotNull(account.getId());
            assertEquals(type, account.getType());
            assertEquals(AccountStatus.ACTIVE, account.getStatus());
            assertEquals(new BigDecimal("100.00"), account.getBalance());
            assertEquals(10, account.getAccountNumber().length());
            assertTrue(account.getAccountNumber().matches("[1-9][0-9]{9}"));
            assertNotNull(account.getCreatedAt());
            assertNull(account.getClosedAt());
        }

        @Test
        @DisplayName("opens an account with a zero balance when no deposit is supplied")
        void opensWithZeroBalance() throws BankingException {
            Account account = fixtures.accountService.openAccount(customer.getId(), AccountType.CHECKING, null);

            assertEquals(0, account.getBalance().compareTo(BigDecimal.ZERO));
            assertEquals(0, fixtures.transactionDao.size(), "no ledger entry for a zero opening deposit");
        }

        @Test
        @DisplayName("records an opening deposit as a DEPOSIT ledger entry")
        void recordsOpeningDeposit() throws BankingException {
            Account account = fixtures.openChecking(customer.getId(), "250.00");

            assertEquals(1, fixtures.transactionDao.size());
            assertEquals(new BigDecimal("250.00"),
                    fixtures.transactionDao.all().get(0).getResultingBalance());
            assertEquals(account.getId(), fixtures.transactionDao.all().get(0).getAccountId());
        }

        @Test
        @DisplayName("enforces the savings minimum balance as an opening requirement")
        void enforcesSavingsMinimum() {
            ValidationException ex = assertThrows(ValidationException.class,
                    () -> fixtures.accountService.openAccount(customer.getId(), AccountType.SAVINGS,
                            new BigDecimal("10.00")));
            assertTrue(ex.getMessage().contains("opening deposit"));
        }

        @Test
        @DisplayName("rejects a negative or oversized opening deposit")
        void rejectsInvalidDeposits() {
            assertThrows(ValidationException.class, () -> fixtures.accountService.openAccount(
                    customer.getId(), AccountType.CHECKING, new BigDecimal("-1.00")));
            assertThrows(ValidationException.class, () -> fixtures.accountService.openAccount(
                    customer.getId(), AccountType.CHECKING, new BigDecimal("2000000.00")));
            assertThrows(ValidationException.class, () -> fixtures.accountService.openAccount(
                    customer.getId(), AccountType.CHECKING, new BigDecimal("10.005")));
        }

        @Test
        @DisplayName("requires a signed-in customer and a known account type")
        void requiresCustomerAndType() {
            assertThrows(ValidationException.class, () -> fixtures.accountService.openAccount(
                    null, AccountType.CHECKING, BigDecimal.ZERO));
            assertThrows(ValidationException.class, () -> fixtures.accountService.openAccount(
                    "  ", AccountType.CHECKING, BigDecimal.ZERO));
            assertThrows(ValidationException.class, () -> fixtures.accountService.openAccount(
                    customer.getId(), null, BigDecimal.ZERO));
        }

        @Test
        @DisplayName("caps the number of open accounts per customer")
        void capsOpenAccounts() throws BankingException {
            BankingRules strictRules = new BankingRules(new BigDecimal("25.00"), BigDecimal.ZERO, 2, 25);
            TestFixtures strict = new TestFixtures(strictRules);
            Customer owner = strict.registerCustomer("capped");
            strict.openChecking(owner.getId(), "0.00");
            strict.openChecking(owner.getId(), "0.00");

            InvalidAccountStateException ex = assertThrows(InvalidAccountStateException.class,
                    () -> strict.openChecking(owner.getId(), "0.00"));
            assertTrue(ex.getMessage().contains("maximum of 2"));
        }

        @Test
        @DisplayName("a closed account does not count toward the open account cap")
        void closedAccountsDoNotCount() throws BankingException {
            BankingRules strictRules = new BankingRules(new BigDecimal("25.00"), BigDecimal.ZERO, 1, 25);
            TestFixtures strict = new TestFixtures(strictRules);
            Customer owner = strict.registerCustomer("recycler");
            Account first = strict.openChecking(owner.getId(), "0.00");
            strict.accountService.closeAccount(owner.getId(), first.getId());

            assertNotNull(strict.openChecking(owner.getId(), "0.00"));
        }
    }

    @Nested
    @DisplayName("US-07 and US-08 listing and balances")
    class Reading {

        @Test
        @DisplayName("lists only the accounts owned by the customer")
        void listsOwnedAccounts() throws BankingException {
            fixtures.openChecking(customer.getId(), "10.00");
            fixtures.openSavings(customer.getId(), "50.00");
            Customer stranger = fixtures.registerCustomer("stranger");
            fixtures.openChecking(stranger.getId(), "999.00");

            List<Account> accounts = fixtures.accountService.listAccounts(customer.getId());

            assertEquals(2, accounts.size());
            assertTrue(accounts.stream().allMatch(account -> account.isOwnedBy(customer.getId())));
            assertEquals(1, fixtures.accountService.listAccounts(stranger.getId()).size());
        }

        @Test
        @DisplayName("returns an empty list for a missing or blank customer id")
        void handlesMissingCustomer() {
            assertTrue(fixtures.accountService.listAccounts(null).isEmpty());
            assertTrue(fixtures.accountService.listAccounts("   ").isEmpty());
        }

        @Test
        @DisplayName("lists only transactable accounts when asked")
        void listsTransactableOnly() throws BankingException {
            Account active = fixtures.openChecking(customer.getId(), "10.00");
            Account toClose = fixtures.openChecking(customer.getId(), "0.00");
            fixtures.accountService.closeAccount(customer.getId(), toClose.getId());

            List<Account> transactable = fixtures.accountService.listTransactableAccounts(customer.getId());

            assertEquals(1, transactable.size());
            assertEquals(active.getId(), transactable.get(0).getId());
        }

        @Test
        @DisplayName("returns the balance for an owned account")
        void returnsBalance() throws BankingException {
            Account account = fixtures.openChecking(customer.getId(), "75.50");

            assertEquals(new BigDecimal("75.50"),
                    fixtures.accountService.getBalance(customer.getId(), account.getId()));
        }

        @Test
        @DisplayName("US-23 blocks access to another customer's account")
        void blocksForeignAccess() throws BankingException {
            Account account = fixtures.openChecking(customer.getId(), "75.50");
            Customer stranger = fixtures.registerCustomer("intruder");

            assertThrows(UnauthorizedAccessException.class,
                    () -> fixtures.accountService.getBalance(stranger.getId(), account.getId()));
            assertThrows(UnauthorizedAccessException.class,
                    () -> fixtures.accountService.getOwnedAccount(stranger.getId(), account.getId()));
        }

        @Test
        @DisplayName("reports a missing account rather than returning null")
        void reportsMissingAccount() {
            assertThrows(ResourceNotFoundException.class,
                    () -> fixtures.accountService.getBalance(customer.getId(), "does-not-exist"));
            assertThrows(ResourceNotFoundException.class,
                    () -> fixtures.accountService.getBalance(customer.getId(), null));
            assertThrows(ResourceNotFoundException.class,
                    () -> fixtures.accountService.getBalance(customer.getId(), "   "));
        }

        @Test
        @DisplayName("looks up an owned account by its account number")
        void findsByAccountNumber() throws BankingException {
            Account account = fixtures.openChecking(customer.getId(), "10.00");

            Account found = fixtures.accountService.getOwnedAccountByNumber(
                    customer.getId(), account.getAccountNumber());

            assertEquals(account.getId(), found.getId());
            assertThrows(ValidationException.class,
                    () -> fixtures.accountService.getOwnedAccountByNumber(customer.getId(), "123"));
            assertThrows(ResourceNotFoundException.class,
                    () -> fixtures.accountService.getOwnedAccountByNumber(customer.getId(), "1234567890"));
        }

        @Test
        @DisplayName("rejects a by-number lookup of an account owned by someone else")
        void rejectsForeignNumberLookup() throws BankingException {
            Account account = fixtures.openChecking(customer.getId(), "10.00");
            Customer stranger = fixtures.registerCustomer("peeker");

            assertThrows(UnauthorizedAccessException.class, () -> fixtures.accountService
                    .getOwnedAccountByNumber(stranger.getId(), account.getAccountNumber()));
        }

        @Test
        @DisplayName("US-14 resolves an active transfer target owned by another customer")
        void resolvesTransferTarget() throws BankingException {
            Customer other = fixtures.registerCustomer("payee");
            Account target = fixtures.openChecking(other.getId(), "0.00");

            assertEquals(target.getId(),
                    fixtures.accountService.findTransferTarget(target.getAccountNumber()).getId());
        }

        @Test
        @DisplayName("refuses a transfer target that is closed")
        void refusesClosedTransferTarget() throws BankingException {
            Customer other = fixtures.registerCustomer("closedpayee");
            Account target = fixtures.openChecking(other.getId(), "0.00");
            fixtures.accountService.closeAccount(other.getId(), target.getId());

            assertThrows(InvalidAccountStateException.class,
                    () -> fixtures.accountService.findTransferTarget(target.getAccountNumber()));
            assertThrows(ResourceNotFoundException.class,
                    () -> fixtures.accountService.findTransferTarget("9999999999"));
        }
    }

    @Nested
    @DisplayName("US-09 closing accounts")
    class Closing {

        @Test
        @DisplayName("closes an account whose balance is zero")
        void closesEmptyAccount() throws BankingException {
            Account account = fixtures.openChecking(customer.getId(), "0.00");

            Account closed = fixtures.accountService.closeAccount(customer.getId(), account.getId());

            assertEquals(AccountStatus.CLOSED, closed.getStatus());
            assertNotNull(closed.getClosedAt());
            assertFalse(closed.isTransactable());
        }

        @Test
        @DisplayName("refuses to close an account that still holds money")
        void refusesToCloseFundedAccount() throws BankingException {
            Account account = fixtures.openChecking(customer.getId(), "5.00");

            InvalidAccountStateException ex = assertThrows(InvalidAccountStateException.class,
                    () -> fixtures.accountService.closeAccount(customer.getId(), account.getId()));
            assertTrue(ex.getMessage().contains("$5.00"));
        }

        @Test
        @DisplayName("refuses to close an already closed account")
        void refusesDoubleClose() throws BankingException {
            Account account = fixtures.openChecking(customer.getId(), "0.00");
            fixtures.accountService.closeAccount(customer.getId(), account.getId());

            assertThrows(InvalidAccountStateException.class,
                    () -> fixtures.accountService.closeAccount(customer.getId(), account.getId()));
        }

        @Test
        @DisplayName("refuses to close a frozen account")
        void refusesFrozenClose() throws BankingException {
            Account account = fixtures.openChecking(customer.getId(), "0.00");
            fixtures.accountDao.updateStatus(account.getId(), AccountStatus.FROZEN, null);

            assertThrows(InvalidAccountStateException.class,
                    () -> fixtures.accountService.closeAccount(customer.getId(), account.getId()));
        }

        @Test
        @DisplayName("refuses to close an account owned by another customer")
        void refusesForeignClose() throws BankingException {
            Account account = fixtures.openChecking(customer.getId(), "0.00");
            Customer stranger = fixtures.registerCustomer("vandal");

            assertThrows(UnauthorizedAccessException.class,
                    () -> fixtures.accountService.closeAccount(stranger.getId(), account.getId()));
        }
    }

    @Test
    @DisplayName("exposes the configured banking rules to callers")
    void exposesRules() {
        assertEquals(new BigDecimal("25.00"), fixtures.accountService.getRules().getSavingsMinimumBalance());
        assertEquals(5, fixtures.accountService.getRules().getMaxAccountsPerCustomer());
        assertEquals(25, fixtures.accountService.getRules().getDefaultHistoryPageSize());
    }
}
