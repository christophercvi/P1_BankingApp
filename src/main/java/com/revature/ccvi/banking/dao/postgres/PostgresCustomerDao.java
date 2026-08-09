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
import com.revature.ccvi.banking.dao.CustomerDao;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.exception.DuplicateResourceException;
import com.revature.ccvi.banking.model.Customer;

/**
 * JDBC implementation of {@link CustomerDao}. Every statement is parameterized, and every
 * resource is released by try-with-resources.
 */
public class PostgresCustomerDao implements CustomerDao {

    private static final String COLUMNS =
            "customer_id, username, email, first_name, last_name, phone, password_hash, created_at, last_login_at";

    private static final String INSERT =
            "INSERT INTO customers (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID =
            "SELECT " + COLUMNS + " FROM customers WHERE customer_id = ?";
    private static final String SELECT_BY_USERNAME =
            "SELECT " + COLUMNS + " FROM customers WHERE username = ?";
    private static final String SELECT_BY_EMAIL =
            "SELECT " + COLUMNS + " FROM customers WHERE email = ?";
    private static final String SELECT_ALL =
            "SELECT " + COLUMNS + " FROM customers ORDER BY created_at";
    private static final String UPDATE_PROFILE =
            "UPDATE customers SET email = ?, first_name = ?, last_name = ?, phone = ? WHERE customer_id = ?";
    private static final String UPDATE_PASSWORD =
            "UPDATE customers SET password_hash = ? WHERE customer_id = ?";
    private static final String UPDATE_LAST_LOGIN =
            "UPDATE customers SET last_login_at = ? WHERE customer_id = ?";
    private static final String DELETE =
            "DELETE FROM customers WHERE customer_id = ?";
    private static final String EXISTS_USERNAME =
            "SELECT 1 FROM customers WHERE username = ?";
    private static final String EXISTS_EMAIL =
            "SELECT 1 FROM customers WHERE email = ?";

    private final PostgresConnectionManager connectionManager;

    public PostgresCustomerDao(PostgresConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Customer create(Customer customer) {
        if (customer.getId() == null || customer.getId().isBlank()) {
            customer.setId(UUID.randomUUID().toString());
        }
        if (customer.getCreatedAt() == null) {
            customer.setCreatedAt(Instant.now());
        }
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, UUID.fromString(customer.getId()));
            statement.setString(2, customer.getUsername());
            statement.setString(3, customer.getEmail());
            statement.setString(4, customer.getFirstName());
            statement.setString(5, customer.getLastName());
            statement.setString(6, customer.getPhone());
            statement.setString(7, customer.getPasswordHash());
            statement.setTimestamp(8, Timestamp.from(customer.getCreatedAt()));
            statement.setTimestamp(9, customer.getLastLoginAt() == null
                    ? null : Timestamp.from(customer.getLastLoginAt()));
            statement.executeUpdate();
            return customer;
        } catch (SQLException ex) {
            if (SqlErrors.isUniqueViolation(ex)) {
                throw new DataAccessException("A customer with that username or email already exists.", ex);
            }
            throw new DataAccessException("Failed to create customer: " + ex.getMessage(), ex);
        }
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        return querySingle(SELECT_BY_ID, statement -> statement.setObject(1, UUID.fromString(customerId)));
    }

    @Override
    public Optional<Customer> findByUsername(String username) {
        return querySingle(SELECT_BY_USERNAME, statement -> statement.setString(1, username));
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        return querySingle(SELECT_BY_EMAIL, statement -> statement.setString(1, email));
    }

    @Override
    public List<Customer> findAll() {
        List<Customer> customers = new ArrayList<>();
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                customers.add(mapRow(resultSet));
            }
            return customers;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to list customers: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean update(Customer customer) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_PROFILE)) {
            statement.setString(1, customer.getEmail());
            statement.setString(2, customer.getFirstName());
            statement.setString(3, customer.getLastName());
            statement.setString(4, customer.getPhone());
            statement.setObject(5, UUID.fromString(customer.getId()));
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            if (SqlErrors.isUniqueViolation(ex)) {
                throw new DataAccessException("That email is already registered to another customer.", ex);
            }
            throw new DataAccessException("Failed to update customer profile: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean updatePasswordHash(String customerId, String passwordHash) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_PASSWORD)) {
            statement.setString(1, passwordHash);
            statement.setObject(2, UUID.fromString(customerId));
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to update password: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean touchLastLogin(String customerId, Instant loginTime) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_LAST_LOGIN)) {
            statement.setTimestamp(1, Timestamp.from(loginTime));
            statement.setObject(2, UUID.fromString(customerId));
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to record login time: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean deleteById(String customerId) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setObject(1, UUID.fromString(customerId));
            return statement.executeUpdate() == 1;
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to delete customer: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean existsByUsername(String username) {
        return exists(EXISTS_USERNAME, username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return exists(EXISTS_EMAIL, email);
    }

    private boolean exists(String sql, String value) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to check customer uniqueness: " + ex.getMessage(), ex);
        }
    }

    private Optional<Customer> querySingle(String sql, StatementBinder binder) {
        try (Connection connection = connectionManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        } catch (SQLException ex) {
            throw new DataAccessException("Failed to load customer: " + ex.getMessage(), ex);
        }
    }

    static Customer mapRow(ResultSet resultSet) throws SQLException {
        Customer customer = new Customer();
        customer.setId(resultSet.getString("customer_id"));
        customer.setUsername(resultSet.getString("username"));
        customer.setEmail(resultSet.getString("email"));
        customer.setFirstName(resultSet.getString("first_name"));
        customer.setLastName(resultSet.getString("last_name"));
        customer.setPhone(resultSet.getString("phone"));
        customer.setPasswordHash(resultSet.getString("password_hash"));
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        customer.setCreatedAt(createdAt == null ? null : createdAt.toInstant());
        Timestamp lastLogin = resultSet.getTimestamp("last_login_at");
        customer.setLastLoginAt(lastLogin == null ? null : lastLogin.toInstant());
        return customer;
    }

    /** Small functional hook so the query plumbing above is written once. */
    @FunctionalInterface
    private interface StatementBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /** Retained for API symmetry with the service layer's duplicate handling. */
    static DuplicateResourceException duplicate(String message) {
        return new DuplicateResourceException(message);
    }
}
