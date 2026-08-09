package com.revature.ccvi.banking.dao;

import java.util.List;
import java.util.Optional;

import com.revature.ccvi.banking.model.Customer;

/**
 * Persistence contract for customers. Implemented once for PostgreSQL and once for MongoDB;
 * the service layer only ever sees this interface (US-19).
 */
public interface CustomerDao {

    Customer create(Customer customer);

    Optional<Customer> findById(String customerId);

    Optional<Customer> findByUsername(String username);

    Optional<Customer> findByEmail(String email);

    List<Customer> findAll();

    boolean update(Customer customer);

    boolean updatePasswordHash(String customerId, String passwordHash);

    boolean touchLastLogin(String customerId, java.time.Instant loginTime);

    boolean deleteById(String customerId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
