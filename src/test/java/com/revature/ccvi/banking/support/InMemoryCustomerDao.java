package com.revature.ccvi.banking.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.revature.ccvi.banking.dao.CustomerDao;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.model.Customer;

/**
 * In-memory {@link CustomerDao} used to exercise the service layer without a live database
 * (project requirement 11). It reproduces the uniqueness behaviour of the real implementations,
 * including throwing on a duplicate insert.
 */
public class InMemoryCustomerDao implements CustomerDao {

    private final Map<String, Customer> store = new LinkedHashMap<>();

    /** Set to simulate a driver level outage on the next write. */
    public boolean failNextWrite;

    @Override
    public Customer create(Customer customer) {
        if (failNextWrite) {
            failNextWrite = false;
            throw new DataAccessException("Simulated database outage.");
        }
        if (customer.getId() == null || customer.getId().isBlank()) {
            customer.setId(UUID.randomUUID().toString());
        }
        if (customer.getCreatedAt() == null) {
            customer.setCreatedAt(Instant.now());
        }
        if (existsByUsername(customer.getUsername()) || existsByEmail(customer.getEmail())) {
            throw new DataAccessException("A customer with that username or email already exists.");
        }
        store.put(customer.getId(), copy(customer));
        return customer;
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        return Optional.ofNullable(store.get(customerId)).map(this::copy);
    }

    @Override
    public Optional<Customer> findByUsername(String username) {
        return store.values().stream()
                .filter(customer -> customer.getUsername().equals(username))
                .findFirst()
                .map(this::copy);
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        return store.values().stream()
                .filter(customer -> customer.getEmail().equals(email))
                .findFirst()
                .map(this::copy);
    }

    @Override
    public List<Customer> findAll() {
        List<Customer> customers = new ArrayList<>(store.values());
        customers.sort(Comparator.comparing(Customer::getCreatedAt));
        return customers.stream().map(this::copy).toList();
    }

    @Override
    public boolean update(Customer customer) {
        Customer stored = store.get(customer.getId());
        if (stored == null) {
            return false;
        }
        boolean emailTaken = store.values().stream()
                .anyMatch(other -> !other.getId().equals(customer.getId())
                        && other.getEmail().equals(customer.getEmail()));
        if (emailTaken) {
            throw new DataAccessException("That email is already registered to another customer.");
        }
        stored.setEmail(customer.getEmail());
        stored.setFirstName(customer.getFirstName());
        stored.setLastName(customer.getLastName());
        stored.setPhone(customer.getPhone());
        return true;
    }

    @Override
    public boolean updatePasswordHash(String customerId, String passwordHash) {
        Customer stored = store.get(customerId);
        if (stored == null) {
            return false;
        }
        stored.setPasswordHash(passwordHash);
        return true;
    }

    @Override
    public boolean touchLastLogin(String customerId, Instant loginTime) {
        Customer stored = store.get(customerId);
        if (stored == null) {
            return false;
        }
        stored.setLastLoginAt(loginTime);
        return true;
    }

    @Override
    public boolean deleteById(String customerId) {
        return store.remove(customerId) != null;
    }

    @Override
    public boolean existsByUsername(String username) {
        return store.values().stream().anyMatch(customer -> customer.getUsername().equals(username));
    }

    @Override
    public boolean existsByEmail(String email) {
        return store.values().stream().anyMatch(customer -> customer.getEmail().equals(email));
    }

    public int size() {
        return store.size();
    }

    /** Copies on the way in and out so tests cannot mutate stored state by accident. */
    private Customer copy(Customer source) {
        return new Customer(source.getId(), source.getUsername(), source.getEmail(), source.getFirstName(),
                source.getLastName(), source.getPhone(), source.getPasswordHash(), source.getCreatedAt(),
                source.getLastLoginAt());
    }
}
