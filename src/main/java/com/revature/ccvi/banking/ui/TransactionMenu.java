package com.revature.ccvi.banking.ui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.revature.ccvi.banking.dao.TransactionFilter;
import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.exception.EndOfInputException;
import com.revature.ccvi.banking.exception.ValidationException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.Transaction;
import com.revature.ccvi.banking.model.TransactionType;
import com.revature.ccvi.banking.service.TransactionService;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Deposit, withdrawal, transfer, and history screens (US-10 through US-18).
 */
class TransactionMenu {

    private final ConsoleIO console;
    private final TransactionService transactionService;
    private final AccountMenu accountMenu;
    private final Session session;

    TransactionMenu(ConsoleIO console, TransactionService transactionService,
                    AccountMenu accountMenu, Session session) {
        this.console = console;
        this.transactionService = transactionService;
        this.accountMenu = accountMenu;
        this.session = session;
    }

    void show() throws EndOfInputException {
        boolean running = true;
        while (running) {
            console.heading("Transactions");
            console.println("  1) Deposit");
            console.println("  2) Withdraw");
            console.println("  3) Transfer");
            console.println("  4) Transaction history (single account)");
            console.println("  5) Transaction history (all accounts)");
            console.println("  6) Back to main menu");
            try {
                switch (console.readMenuChoice("\nSelect an option [1-6]: ", 1, 6)) {
                    case 1 -> deposit();
                    case 2 -> withdraw();
                    case 3 -> transfer();
                    case 4 -> singleAccountHistory();
                    case 5 -> combinedHistory();
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

    private void deposit() throws BankingException {
        Account account = accountMenu.selectTransactableAccount("Deposit into which account?");
        if (account == null) {
            return;
        }
        BigDecimal amount = console.readAmount("Deposit amount: ");
        String memo = console.readOptional("Description (optional): ");
        Transaction transaction = transactionService.deposit(
                session.getCustomerId(), account.getId(), amount, memo);
        console.blank();
        console.success("Deposited " + MoneyUtil.format(transaction.getAmount()) + " into account "
                + account.getAccountNumber() + ".");
        console.info("New balance: " + MoneyUtil.format(transaction.getResultingBalance()));
        console.pause();
    }

    private void withdraw() throws BankingException {
        Account account = accountMenu.selectTransactableAccount("Withdraw from which account?");
        if (account == null) {
            return;
        }
        BigDecimal amount = console.readAmount("Withdrawal amount: ");
        String memo = console.readOptional("Description (optional): ");
        Transaction transaction = transactionService.withdraw(
                session.getCustomerId(), account.getId(), amount, memo);
        console.blank();
        console.success("Withdrew " + MoneyUtil.format(transaction.getAmount()) + " from account "
                + account.getAccountNumber() + ".");
        console.info("New balance: " + MoneyUtil.format(transaction.getResultingBalance()));
        console.pause();
    }

    private void transfer() throws BankingException {
        Account source = accountMenu.selectTransactableAccount("Transfer from which account?");
        if (source == null) {
            return;
        }
        console.info("Enter the 10 digit destination account number. It may belong to another customer.");
        String destination = console.readRequired("Destination account number: ", "Destination account number");
        BigDecimal amount = console.readAmount("Transfer amount: ");
        String memo = console.readOptional("Description (optional): ");
        TransactionService.TransferReceipt receipt = transactionService.transfer(
                session.getCustomerId(), source.getId(), destination, amount, memo);
        console.blank();
        console.success("Transferred " + MoneyUtil.format(receipt.amount()) + " from account "
                + receipt.sourceAccountNumber() + " to account " + receipt.destinationAccountNumber() + ".");
        console.info("Your new balance: " + MoneyUtil.format(receipt.sourceBalance()));
        console.pause();
    }

    private void singleAccountHistory() throws BankingException {
        Account account = accountMenu.selectAccount("View history for which account?");
        if (account == null) {
            return;
        }
        TransactionFilter filter = buildFilter();
        List<Transaction> transactions = transactionService.getHistory(
                session.getCustomerId(), account.getId(), filter);
        console.heading("History for account " + account.getAccountNumber());
        ConsoleFormatter.printTransactions(console, transactions);
        console.pause();
    }

    private void combinedHistory() throws BankingException {
        TransactionFilter filter = buildFilter();
        List<Transaction> transactions = transactionService.getCombinedHistory(session.getCustomerId(), filter);
        console.heading("History across all of your accounts");
        ConsoleFormatter.printTransactions(console, transactions);
        console.pause();
    }

    /** US-17: optional type and date-range filtering built from console prompts. */
    private TransactionFilter buildFilter() throws ValidationException, EndOfInputException {
        TransactionFilter.Builder builder = TransactionFilter.builder().limit(100);
        console.heading("Filter Options");
        console.println("  1) All transaction types");
        console.println("  2) Deposits only");
        console.println("  3) Withdrawals only");
        console.println("  4) Transfers in only");
        console.println("  5) Transfers out only");
        int typeChoice = console.readMenuChoice("\nFilter by type [1-5]: ", 1, 5);
        switch (typeChoice) {
            case 2 -> builder.type(TransactionType.DEPOSIT);
            case 3 -> builder.type(TransactionType.WITHDRAWAL);
            case 4 -> builder.type(TransactionType.TRANSFER_IN);
            case 5 -> builder.type(TransactionType.TRANSFER_OUT);
            default -> { }
        }
        if (console.readYesNo("Filter by date range? (y/N): ")) {
            LocalDate from = console.readDate("Start date (yyyy-MM-dd): ");
            LocalDate to = console.readDate("End date (yyyy-MM-dd): ");
            if (from.isAfter(to)) {
                throw new ValidationException("The start date must not be after the end date.");
            }
            ZoneId zone = ZoneId.systemDefault();
            builder.from(from.atStartOfDay(zone).toInstant());
            builder.to(to.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant());
        }
        return builder.build();
    }
}
