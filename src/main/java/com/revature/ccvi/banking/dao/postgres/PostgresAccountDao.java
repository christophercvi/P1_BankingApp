package com.revature.ccvi.banking.dao.postgres;

import java.math.BigDecimal;
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
import com.revature.ccvi.banking.dao.AccountDao;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountStatus;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.util.MoneyUtil;

/**
 * JDBC implementation of {@link AccountDao}. Balance updates use a single conditional UPDATE
 * with a RETURNING clause, which makes the guard and the write one atomic statement.
 */
public class PostgresAccountDao implements AccountDao {

    private static final String COLUMNS =
            "account_id, account_number, customer_id, account_type, status, balance, created_at, closed_at";

    private static final String INSERT =
            "INSERT INTO accounts (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID =
            "SELECT " + COLUMNS + " FROM accounts WHERE account_id = ?";
    private static final String SELECT_BY_NUMBER =
            "SELECT " + COLUMNS + " FROM accounts WHERE account_number = ?";
    private static final String SELECT_BY_CUSTOMER =
            "SELECT " + COLUMNS + " FROM accounts WHERE customer_id = ? ORDER BY created_at";
    private static final String SELECT_ALL =
            "SELECT " + COLUMNS + " FROM accounts ORDER BY created_at";
    private static final String UPDATE_STATUS =
            "UPDATE accounts SET status = ?, closed_at = ? WHERE account_id = ?";
    private static final String APPLY_DELTA =
            "UPDATE accounts SET balance = balance + ? WHERE account_id = ? AND balance + ? >= ? "
            + "RETURNING balance";
    private static final String EXISTS_NUMBER =
            "SELECT 1 FROM accounts WHERE account_number = ?";
    private static final String DELETE =
            "DELETE FROM accounts WHERE account_id = ?";

    private final PostgresConnectionManager connectionManager;

    public PostgresAccountDao(PostgresConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Account create(Account account) {
        if (account.getId() == null || account.getId().isBlank()) {
            account.setId(UUID.randomUUID().toString());
        }
        if (account.getCreatedAt() == null) {
            account.setCreatedAt(Instant.now());
        }
        account.setBalance(MoneyUtil.normalize(
                account.getBalance() == null ? MoneyUtil.zero() : account.getBalance()));
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, UUID.fromString(account.getId()));
            statement.setString(2, account.getAccountNumber());
            statement.setObject(3, UUID.fromString(account.getCustomerId()));
            statement.setString(4, account.getType().name());
            statement.setString(5, account.getStatus().name());
            statement.setBigDecimal(6, account.getBalance());
            statement.setTimestamp(7, Timestamp.from(account.getCreatedAt()));
            statement.setTimestamp(8, account.getClosedAt() == null ? null : Timestamp.from(account.getClosedAt()));
            statement.executeUpdate();
            return account;
        } catch (SQLException ex) {
            if (SqlErrors.isUniqueViolation(ex)) {
                throw new DataAccessException("That account number is already in use.", ex);
            }
            if (SqlErrors.isForeignKeyViolation(ex)) {
                throw new DataAccessException("Cannot open an account for a customer that does not exist.", ex);
            }
            throw new DataAccessException("Failed to open account: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Account> findById(String accountId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setObject(1, UUID.fromString(accountId));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to load account: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_NUMBER)) {
            statement.setString(1, accountNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to look up account by number: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Account> findByCustomerId(String customerId) {
        List<Account> accounts = new ArrayList<>();
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_CUSTOMER)) {
            statement.setObject(1, UUID.fromString(customerId));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    accounts.add(mapRow(resultSet));
                }
            }
            return accounts;
        } catch (IllegalArgumentException ex) {
            return List.of();
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to list customer accounts: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Account> findAll() {
        List<Account> accounts = new ArrayList<>();
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                accounts.add(mapRow(resultSet));
            }
            return accounts;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to list accounts: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean updateStatus(String accountId, AccountStatus status, Instant closedAt) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_STATUS)) {
            statement.setString(1, status.name());
            statement.setTimestamp(2, closedAt == null ? null : Timestamp.from(closedAt));
            statement.setObject(3, UUID.fromString(accountId));
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to update account status: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<BigDecimal> applyBalanceDelta(String accountId, BigDecimal delta,
                                                  BigDecimal minimumResultingBalance) {
        BigDecimal normalizedDelta = MoneyUtil.normalize(delta);
        BigDecimal floor = MoneyUtil.normalize(
                minimumResultingBalance == null ? MoneyUtil.zero() : minimumResultingBalance);
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(APPLY_DELTA)) {
            statement.setBigDecimal(1, normalizedDelta);
            statement.setObject(2, UUID.fromString(accountId));
            statement.setBigDecimal(3, normalizedDelta);
            statement.setBigDecimal(4, floor);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? Optional.of(MoneyUtil.normalize(resultSet.getBigDecimal(1)))
                        : Optional.empty();
            }
        } catch (SQLException ex) {
            if (SqlErrors.isCheckViolation(ex)) {
                return Optional.empty();
            }
            throw new DataAccessException("Failed to update account balance: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean existsByAccountNumber(String accountNumber) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_NUMBER)) {
            statement.setString(1, accountNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to check account number: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean deleteById(String accountId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setObject(1, UUID.fromString(accountId));
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to delete account: " + ex.getMessage(), ex);
        }
    }

    static Account mapRow(ResultSet resultSet) throws SQLException {
        Account account = new Account();
        account.setId(resultSet.getString("account_id"));
        account.setAccountNumber(resultSet.getString("account_number").trim());
        account.setCustomerId(resultSet.getString("customer_id"));
        account.setType(AccountType.from(resultSet.getString("account_type")));
        account.setStatus(AccountStatus.from(resultSet.getString("status")));
        account.setBalance(MoneyUtil.normalize(resultSet.getBigDecimal("balance")));
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        account.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        Timestamp closedAt = resultSet.getTimestamp("closed_at");
        account.setClosedAt(closedAt == null ? null : closedAt.toInstant());
        return account;
    }
}
