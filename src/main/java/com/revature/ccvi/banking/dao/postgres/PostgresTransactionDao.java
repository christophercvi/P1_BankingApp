package com.revature.ccvi.banking.dao.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.revature.ccvi.banking.config.PostgresConnectionManager;
import com.revature.ccvi.banking.dao.TransactionDao;
import com.revature.ccvi.banking.dao.TransactionFilter;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Transaction;
import com.revature.ccvi.banking.model.TransactionType;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * JDBC implementation of {@link TransactionDao}. Filter clauses are assembled dynamically but
 * every value is still bound as a parameter, never concatenated into the SQL text.
 */
public class PostgresTransactionDao implements TransactionDao {

    private static final String COLUMNS = "transaction_id, account_id, counterparty_account_id, transaction_type, "
            + "amount, resulting_balance, description, created_at";

    private static final String INSERT =
            "INSERT INTO transactions (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID =
            "SELECT " + COLUMNS + " FROM transactions WHERE transaction_id = ?";
    private static final String COUNT_BY_ACCOUNT =
            "SELECT COUNT(*) FROM transactions WHERE account_id = ?";
    private static final String DELETE_BY_ACCOUNT =
            "DELETE FROM transactions WHERE account_id = ?";

    private final PostgresConnectionManager connectionManager;

    public PostgresTransactionDao(PostgresConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Transaction create(Transaction transaction) {
        if (transaction.getId() == null || transaction.getId().isBlank()) {
            transaction.setId(UUID.randomUUID().toString());
        }
        if (transaction.getCreatedAt() == null) {
            transaction.setCreatedAt(Instant.now());
        }
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            bindInsert(statement, transaction);
            statement.executeUpdate();
            return transaction;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to record transaction: " + ex.getMessage(), ex);
        }
    }

    static void bindInsert(PreparedStatement statement, Transaction transaction) throws SQLException {
        statement.setObject(1, UUID.fromString(transaction.getId()));
        statement.setObject(2, UUID.fromString(transaction.getAccountId()));
        statement.setObject(3, transaction.getCounterpartyAccountId() == null
                ? null : UUID.fromString(transaction.getCounterpartyAccountId()));
        statement.setString(4, transaction.getType().name());
        statement.setBigDecimal(5, MoneyUtil.normalize(transaction.getAmount()));
        statement.setBigDecimal(6, MoneyUtil.normalize(transaction.getResultingBalance()));
        statement.setString(7, transaction.getDescription());
        statement.setTimestamp(8, Timestamp.from(transaction.getCreatedAt()));
    }

    @Override
    public Optional<Transaction> findById(String transactionId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setObject(1, UUID.fromString(transactionId));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to load transaction: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Transaction> findByAccountId(String accountId, TransactionFilter filter) {
        return findByAccountIds(List.of(accountId), filter);
    }

    @Override
    public List<Transaction> findByAccountIds(List<String> accountIds, TransactionFilter filter) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }
        TransactionFilter effective = filter == null ? TransactionFilter.none() : filter;
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS).append(" FROM transactions WHERE account_id IN (");
        for (int index = 0; index < accountIds.size(); index++) {
            sql.append(index == 0 ? "?" : ", ?");
            parameters.add(UUID.fromString(accountIds.get(index)));
        }
        sql.append(')');
        if (effective.getType().isPresent()) {
            sql.append(" AND transaction_type = ?");
            parameters.add(effective.getType().get().name());
        }
        if (effective.getFrom().isPresent()) {
            sql.append(" AND created_at >= ?");
            parameters.add(Timestamp.from(effective.getFrom().get()));
        }
        if (effective.getTo().isPresent()) {
            sql.append(" AND created_at <= ?");
            parameters.add(Timestamp.from(effective.getTo().get()));
        }
        sql.append(" ORDER BY created_at DESC, transaction_id DESC");
        if (effective.hasLimit()) {
            sql.append(" LIMIT ?");
            parameters.add(effective.getLimit());
        }

        List<Transaction> transactions = new ArrayList<>();
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    transactions.add(mapRow(resultSet));
                }
            }
            return transactions;
        } catch (IllegalArgumentException ex) {
            return List.of();
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to load transaction history: " + ex.getMessage(), ex);
        }
    }

    @Override
    public long countByAccountId(String accountId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_BY_ACCOUNT)) {
            statement.setObject(1, UUID.fromString(accountId));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to count transactions: " + ex.getMessage(), ex);
        }
    }

    @Override
    public int deleteByAccountId(String accountId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_ACCOUNT)) {
            statement.setObject(1, UUID.fromString(accountId));
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to delete transactions: " + ex.getMessage(), ex);
        }
    }

    static Transaction mapRow(ResultSet resultSet) throws SQLException {
        Transaction transaction = new Transaction();
        transaction.setId(resultSet.getString("transaction_id"));
        transaction.setAccountId(resultSet.getString("account_id"));
        transaction.setCounterpartyAccountId(resultSet.getString("counterparty_account_id"));
        transaction.setType(TransactionType.from(resultSet.getString("transaction_type")));
        transaction.setAmount(MoneyUtil.normalize(resultSet.getBigDecimal("amount")));
        transaction.setResultingBalance(MoneyUtil.normalize(resultSet.getBigDecimal("resulting_balance")));
        transaction.setDescription(resultSet.getString("description"));
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        transaction.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        return transaction;
    }
}
