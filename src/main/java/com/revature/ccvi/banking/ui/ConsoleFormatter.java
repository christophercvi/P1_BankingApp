package com.revature.ccvi.banking.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.Transaction;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Renders domain objects as fixed width console tables.
 */
final class ConsoleFormatter {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private ConsoleFormatter() {
    }

    static void printAccounts(ConsoleIO console, List<Account> accounts) {
        if (accounts.isEmpty()) {
            console.info("You do not have any accounts yet. Choose 'Open a new account' to get started.");
            return;
        }
        console.println(String.format("  %-3s %-12s %-9s %-8s %14s  %s", "#", "Number", "Type", "Status",
                "Balance", "Opened"));
        console.println("  " + "-".repeat(72));
        for (int index = 0; index < accounts.size(); index++) {
            Account account = accounts.get(index);
            console.println(String.format("  %-3d %-12s %-9s %-8s %14s  %s",
                    index + 1,
                    account.getAccountNumber(),
                    account.getType(),
                    account.getStatus(),
                    MoneyUtil.format(account.getBalance()),
                    account.getCreatedAt() == null ? "-" : TIMESTAMP.format(account.getCreatedAt())));
        }
    }

    static void printTransactions(ConsoleIO console, List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            console.info("No transactions match the selected criteria.");
            return;
        }
        console.println(String.format("  %-20s %-13s %14s %16s  %s",
                "Date", "Type", "Amount", "Balance After", "Description"));
        console.println("  " + "-".repeat(92));
        for (Transaction transaction : transactions) {
            console.println(String.format("  %-20s %-13s %14s %16s  %s",
                    transaction.getCreatedAt() == null ? "-" : TIMESTAMP.format(transaction.getCreatedAt()),
                    transaction.getType(),
                    MoneyUtil.format(transaction.getAmount()),
                    MoneyUtil.format(transaction.getResultingBalance()),
                    transaction.getDescription() == null ? "" : transaction.getDescription()));
        }
        console.info(transactions.size() + " transaction(s) shown, newest first.");
    }
}
