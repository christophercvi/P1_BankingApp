package com.revature.ccvi.banking.dao.postgres;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.revature.ccvi.banking.config.PostgresConnectionManager;
import com.revature.ccvi.banking.dao.TransferExecutor;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.TransactionType;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * Performs a transfer inside one JDBC transaction (US-15). Both rows are locked with
 * {@code SELECT ... FOR UPDATE} in a deterministic id order to avoid deadlocks between two
 * concurrent transfers moving money in opposite directions.
 */
public class PostgresTransferExecutor implements TransferExecutor {

    private static final String LOCK_ROW =
            "SELECT balance FROM accounts WHERE account_id = ? FOR UPDATE";
    private static final String UPDATE_BALANCE =
            "UPDATE accounts SET balance = ? WHERE account_id = ?";
    private static final String INSERT_LEDGER = "INSERT INTO transactions (transaction_id, account_id, "
            + "counterparty_account_id, transaction_type, amount, resulting_balance, description, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private final PostgresConnectionManager connectionManager;

    public PostgresTransferExecutor(PostgresConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public TransferOutcome transfer(String sourceAccountId, String destinationAccountId, BigDecimal amount,
                                    BigDecimal minimumSourceBalance, String description) {
        BigDecimal transferAmount = MoneyUtil.normalize(amount);
        BigDecimal floor = MoneyUtil.normalize(
                minimumSourceBalance == null ? MoneyUtil.zero() : minimumSourceBalance);
        UUID source = UUID.fromString(sourceAccountId);
        UUID destination = UUID.fromString(destinationAccountId);

        Connection connection = null;
        try {
            connection = connectionManager.getConnection();
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            // Lock in a stable order so opposing transfers cannot deadlock.
            boolean sourceFirst = source.compareTo(destination) <= 0;
            UUID firstLock = sourceFirst ? source : destination;
            UUID secondLock = sourceFirst ? destination : source;
            BigDecimal firstBalance = lockBalance(connection, firstLock);
            BigDecimal secondBalance = lockBalance(connection, secondLock);
            BigDecimal sourceBalance = sourceFirst ? firstBalance : secondBalance;
            BigDecimal destinationBalance = sourceFirst ? secondBalance : firstBalance;

            if (sourceBalance == null || destinationBalance == null) {
                connection.rollback();
                throw new DataAccessException("One of the accounts in the transfer no longer exists.");
            }

            BigDecimal newSourceBalance = MoneyUtil.normalize(sourceBalance.subtract(transferAmount));
            if (newSourceBalance.compareTo(floor) < 0) {
                connection.rollback();
                return TransferOutcome.rejected();
            }
            BigDecimal newDestinationBalance = MoneyUtil.normalize(destinationBalance.add(transferAmount));

            updateBalance(connection, source, newSourceBalance);
            updateBalance(connection, destination, newDestinationBalance);
            Instant now = Instant.now();
            insertLedgerEntry(connection, source, destination, TransactionType.TRANSFER_OUT,
                    transferAmount, newSourceBalance, description, now);
            insertLedgerEntry(connection, destination, source, TransactionType.TRANSFER_IN,
                    transferAmount, newDestinationBalance, description, now);

            connection.commit();
            return TransferOutcome.applied(newSourceBalance, newDestinationBalance);
        } catch (SQLException ex) {
            rollbackQuietly(connection);
            throw new DataAccessException("Transfer failed and was rolled back: " + ex.getMessage(), ex);
        } catch (DataAccessException ex) {
            rollbackQuietly(connection);
            throw ex;
        } finally {
            closeQuietly(connection);
        }
    }

    private BigDecimal lockBalance(Connection connection, UUID accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_ROW)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? MoneyUtil.normalize(resultSet.getBigDecimal(1)) : null;
            }
        }
    }

    private void updateBalance(Connection connection, UUID accountId, BigDecimal balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_BALANCE)) {
            statement.setBigDecimal(1, balance);
            statement.setObject(2, accountId);
            statement.executeUpdate();
        }
    }

    private void insertLedgerEntry(Connection connection, UUID accountId, UUID counterpartyId, TransactionType type,
                                   BigDecimal amount, BigDecimal resultingBalance, String description,
                                   Instant createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_LEDGER)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, accountId);
            statement.setObject(3, counterpartyId);
            statement.setString(4, type.name());
            statement.setBigDecimal(5, amount);
            statement.setBigDecimal(6, resultingBalance);
            statement.setString(7, description);
            statement.setTimestamp(8, Timestamp.from(createdAt));
            statement.executeUpdate();
        }
    }

    private void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Nothing further can be done; the outer exception already describes the failure.
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(true);
            connection.close();
        } catch (SQLException ignored) {
            // Connection is being discarded anyway.
        }
    }
}
