package com.revature.ccvi.banking.service;

/**
 * Credential hashing abstraction (US-04). Keeping this behind an interface lets unit tests swap
 * in a fast stub instead of paying the deliberate cost of a memory hard KDF on every test.
 */
public interface PasswordEncoder {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String storedHash);
}
