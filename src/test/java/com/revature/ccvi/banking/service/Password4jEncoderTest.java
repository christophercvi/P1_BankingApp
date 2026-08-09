package com.revature.ccvi.banking.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revature.ccvi.banking.config.AppConfig;

/**
 * Verifies the real Argon2id encoder. Cost parameters are reduced here so the suite stays fast
 * while still exercising the production code path.
 */
@DisplayName("Password4jEncoder: Argon2id hashing (US-04)")
class Password4jEncoderTest {

    private static PasswordEncoder encoder;

    @BeforeAll
    static void setUp() {
        Properties properties = new Properties();
        properties.setProperty("security.argon2.memoryKib", "1024");
        properties.setProperty("security.argon2.iterations", "1");
        properties.setProperty("security.argon2.parallelism", "1");
        properties.setProperty("security.argon2.outputLengthBytes", "32");
        encoder = new Password4jEncoder(AppConfig.fromProperties(properties));
    }

    @Test
    @DisplayName("produces an Argon2id encoded hash that never contains the plaintext")
    void producesArgon2idHash() {
        String hash = encoder.hash("CorrectHorse1");

        assertTrue(hash.startsWith("$argon2id$"), "expected an argon2id encoded hash but got: " + hash);
        assertFalse(hash.contains("CorrectHorse1"));
    }

    @Test
    @DisplayName("uses a fresh random salt so identical passwords hash differently")
    void saltsEachHash() {
        assertNotEquals(encoder.hash("SamePassword1"), encoder.hash("SamePassword1"));
    }

    @Test
    @DisplayName("verifies the correct password and rejects a wrong one")
    void verifiesPasswords() {
        String hash = encoder.hash("CorrectHorse1");

        assertTrue(encoder.matches("CorrectHorse1", hash));
        assertFalse(encoder.matches("correcthorse1", hash));
        assertFalse(encoder.matches("WrongPassword1", hash));
    }

    @Test
    @DisplayName("returns false rather than throwing for malformed or missing input")
    void handlesBadInputSafely() {
        String hash = encoder.hash("CorrectHorse1");

        assertFalse(encoder.matches(null, hash));
        assertFalse(encoder.matches("", hash));
        assertFalse(encoder.matches("CorrectHorse1", null));
        assertFalse(encoder.matches("CorrectHorse1", "   "));
        assertFalse(encoder.matches("CorrectHorse1", "not-a-valid-hash"));
    }

    @Test
    @DisplayName("refuses to hash an empty credential")
    void refusesEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> encoder.hash(null));
        assertThrows(IllegalArgumentException.class, () -> encoder.hash(""));
    }

    @Test
    @DisplayName("verifies a hash produced with different cost parameters")
    void verifiesAcrossCostChanges() {
        Properties cheap = new Properties();
        cheap.setProperty("security.argon2.memoryKib", "512");
        cheap.setProperty("security.argon2.iterations", "1");
        String oldHash = new Password4jEncoder(AppConfig.fromProperties(cheap)).hash("Rotated123");

        // The verifier reads the parameters back out of the stored hash, so an upgraded
        // configuration still validates credentials created under the old settings.
        assertTrue(encoder.matches("Rotated123", oldHash));
    }
}
