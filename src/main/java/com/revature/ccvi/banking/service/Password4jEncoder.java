package com.revature.ccvi.banking.service;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import com.revature.ccvi.banking.config.AppConfig;

/**
 * Argon2id credential hashing backed by Password4j.
 *
 * <p>Each call to {@link #hash(String)} generates a fresh random salt, and the encoded output
 * carries the salt plus the cost parameters, so verification never needs a side table. Argon2id
 * is used rather than a bare digest because it is memory hard and therefore resistant to GPU and
 * ASIC accelerated cracking.</p>
 */
public class Password4jEncoder implements PasswordEncoder {

    private static final int SALT_BYTES = 16;

    private final Argon2Function function;

    public Password4jEncoder() {
        this(AppConfig.getInstance());
    }

    public Password4jEncoder(AppConfig config) {
        this(Argon2Function.getInstance(
                config.getInt("security.argon2.memoryKib", 15360),
                config.getInt("security.argon2.iterations", 3),
                config.getInt("security.argon2.parallelism", 1),
                config.getInt("security.argon2.outputLengthBytes", 32),
                Argon2.ID));
    }

    public Password4jEncoder(Argon2Function function) {
        this.function = function;
    }

    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Cannot hash an empty password");
        }
        return Password.hash(rawPassword)
                .addRandomSalt(SALT_BYTES)
                .with(function)
                .getResult();
    }

    @Override
    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || rawPassword.isEmpty() || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            // Parameters are read back from the encoded hash so old hashes stay verifiable
            // even after the configured cost factors change.
            Argon2Function verifier = Argon2Function.getInstanceFromHash(storedHash);
            return Password.check(rawPassword, storedHash).with(verifier);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
