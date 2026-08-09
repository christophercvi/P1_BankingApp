package com.revature.ccvi.banking.support;

import com.revature.ccvi.banking.service.PasswordEncoder;

/**
 * Deterministic, cheap encoder for service tests. Argon2id is intentionally slow, so running it
 * inside every test would dominate the suite runtime; the real encoder is covered separately by
 * {@code Password4jEncoderTest}.
 */
public class FakePasswordEncoder implements PasswordEncoder {

    private static final String PREFIX = "fake$";

    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Cannot hash an empty password");
        }
        return PREFIX + rawPassword.hashCode();
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || rawPassword.isEmpty() || storedHash == null) {
            return false;
        }
        return storedHash.equals(hash(rawPassword));
    }
}
