package com.revature.ccvi.banking.service;

import java.time.Instant;
import java.util.Optional;

import com.revature.ccvi.banking.dao.CustomerDao;
import com.revature.ccvi.banking.exception.AuthenticationException;
import com.revature.ccvi.banking.exception.DuplicateResourceException;
import com.revature.ccvi.banking.exception.ResourceNotFoundException;
import com.revature.ccvi.banking.exception.ValidationException;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.util.InputValidator;

/**
 * Registration, authentication, and profile maintenance (US-01 through US-04).
 *
 * <p>Plaintext passwords are used only long enough to hash or verify them; nothing here writes a
 * raw credential to the database or to any message.</p>
 */
public class AuthService {

    private final CustomerDao customerDao;
    private final PasswordEncoder passwordEncoder;

    public AuthService(CustomerDao customerDao, PasswordEncoder passwordEncoder) {
        this.customerDao = customerDao;
        this.passwordEncoder = passwordEncoder;
    }

    /** US-01: creates a customer after validating input and enforcing uniqueness. */
    public Customer register(String username, String password, String email,
                             String firstName, String lastName, String phone)
            throws ValidationException, DuplicateResourceException {
        String normalizedUsername = InputValidator.validateUsername(username);
        String normalizedEmail = InputValidator.validateEmail(email);
        String validFirstName = InputValidator.validateName(firstName, "First name");
        String validLastName = InputValidator.validateName(lastName, "Last name");
        String validPhone = InputValidator.validateOptionalPhone(phone);
        InputValidator.validatePassword(password);

        if (customerDao.existsByUsername(normalizedUsername)) {
            throw new DuplicateResourceException("Username '" + normalizedUsername + "' is already taken.");
        }
        if (customerDao.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Email '" + normalizedEmail + "' is already registered.");
        }

        Customer customer = new Customer();
        customer.setUsername(normalizedUsername);
        customer.setEmail(normalizedEmail);
        customer.setFirstName(validFirstName);
        customer.setLastName(validLastName);
        customer.setPhone(validPhone);
        customer.setPasswordHash(passwordEncoder.hash(password));
        customer.setCreatedAt(Instant.now());
        return customerDao.create(customer);
    }

    /**
     * US-02: verifies credentials. The same message is returned for an unknown username and a
     * wrong password so the response cannot be used to enumerate accounts.
     */
    public Customer login(String username, String password) throws ValidationException, AuthenticationException {
        String normalizedUsername = InputValidator.requireText(username, "Username").toLowerCase();
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Password is required.");
        }
        Optional<Customer> found = customerDao.findByUsername(normalizedUsername);
        if (found.isEmpty() || !passwordEncoder.matches(password, found.get().getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password.");
        }
        Customer customer = found.get();
        Instant now = Instant.now();
        customerDao.touchLastLogin(customer.getId(), now);
        customer.setLastLoginAt(now);
        return customer;
    }

    /** US-03: updates the mutable profile fields; username is immutable by design. */
    public Customer updateProfile(String customerId, String email, String firstName, String lastName, String phone)
            throws ValidationException, ResourceNotFoundException, DuplicateResourceException {
        Customer customer = requireCustomer(customerId);
        String normalizedEmail = InputValidator.validateEmail(email);
        String validFirstName = InputValidator.validateName(firstName, "First name");
        String validLastName = InputValidator.validateName(lastName, "Last name");
        String validPhone = InputValidator.validateOptionalPhone(phone);

        if (!normalizedEmail.equals(customer.getEmail())) {
            Optional<Customer> emailOwner = customerDao.findByEmail(normalizedEmail);
            if (emailOwner.isPresent() && !emailOwner.get().getId().equals(customerId)) {
                throw new DuplicateResourceException("Email '" + normalizedEmail + "' is already registered.");
            }
        }

        customer.setEmail(normalizedEmail);
        customer.setFirstName(validFirstName);
        customer.setLastName(validLastName);
        customer.setPhone(validPhone);
        if (!customerDao.update(customer)) {
            throw new ResourceNotFoundException("Customer profile could not be updated because it no longer exists.");
        }
        return customer;
    }

    /** US-04: rotates the stored hash after re-verifying the current password. */
    public void changePassword(String customerId, String currentPassword, String newPassword)
            throws ValidationException, AuthenticationException, ResourceNotFoundException {
        Customer customer = requireCustomer(customerId);
        if (!passwordEncoder.matches(currentPassword, customer.getPasswordHash())) {
            throw new AuthenticationException("The current password you entered is incorrect.");
        }
        InputValidator.validatePassword(newPassword);
        if (passwordEncoder.matches(newPassword, customer.getPasswordHash())) {
            throw new ValidationException("The new password must be different from the current password.");
        }
        String newHash = passwordEncoder.hash(newPassword);
        if (!customerDao.updatePasswordHash(customerId, newHash)) {
            throw new ResourceNotFoundException("Password could not be updated because the customer no longer exists.");
        }
        customer.setPasswordHash(newHash);
    }

    public Customer findById(String customerId) throws ResourceNotFoundException {
        return requireCustomer(customerId);
    }

    private Customer requireCustomer(String customerId) throws ResourceNotFoundException {
        if (customerId == null || customerId.isBlank()) {
            throw new ResourceNotFoundException("No customer is currently signed in.");
        }
        return customerDao.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer record not found."));
    }
}
