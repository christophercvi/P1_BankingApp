package com.revature.ccvi.banking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.revature.ccvi.banking.exception.AuthenticationException;
import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.exception.DuplicateResourceException;
import com.revature.ccvi.banking.exception.ResourceNotFoundException;
import com.revature.ccvi.banking.exception.ValidationException;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.support.TestFixtures;

@DisplayName("AuthService: registration, login, profile, and credential rules")
class AuthServiceTest {

    private TestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures();
    }

    @Nested
    @DisplayName("US-01 registration")
    class Registration {

        @Test
        @DisplayName("registers a customer, normalizing username and email to lower case")
        void registersCustomer() throws BankingException {
            Customer customer = fixtures.authService.register("JaneDoe", "Passw0rd1", "Jane.Doe@Example.COM",
                    "Jane", "Doe", "(555) 123-4567");

            assertNotNull(customer.getId());
            assertEquals("janedoe", customer.getUsername());
            assertEquals("jane.doe@example.com", customer.getEmail());
            assertEquals("Jane", customer.getFirstName());
            assertNotNull(customer.getCreatedAt());
            assertEquals(1, fixtures.customerDao.size());
        }

        @Test
        @DisplayName("US-04 never stores the raw password")
        void storesOnlyAHash() throws BankingException {
            Customer customer = fixtures.authService.register("hashcheck", "Secret123", "hash@example.com",
                    "Hash", "Check", null);

            assertNotEquals("Secret123", customer.getPasswordHash());
            assertFalse(customer.getPasswordHash().contains("Secret123"));
            assertTrue(fixtures.passwordEncoder.matches("Secret123", customer.getPasswordHash()));
            assertFalse(customer.toString().contains(customer.getPasswordHash()));
        }

        @Test
        @DisplayName("treats a blank phone number as not provided")
        void allowsBlankPhone() throws BankingException {
            Customer customer = fixtures.authService.register("nophone", "Passw0rd1", "nophone@example.com",
                    "No", "Phone", "   ");

            assertNull(customer.getPhone());
        }

        @Test
        @DisplayName("rejects a duplicate username")
        void rejectsDuplicateUsername() throws BankingException {
            fixtures.authService.register("dupuser", "Passw0rd1", "first@example.com", "First", "User", null);

            DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
                    () -> fixtures.authService.register("DupUser", "Passw0rd1", "second@example.com",
                            "Second", "User", null));
            assertTrue(ex.getMessage().contains("already taken"));
            assertEquals(1, fixtures.customerDao.size());
        }

        @Test
        @DisplayName("rejects a duplicate email regardless of letter case")
        void rejectsDuplicateEmail() throws BankingException {
            fixtures.authService.register("firstuser", "Passw0rd1", "shared@example.com", "First", "User", null);

            assertThrows(DuplicateResourceException.class,
                    () -> fixtures.authService.register("seconduser", "Passw0rd1", "SHARED@example.com",
                            "Second", "User", null));
        }

        @ParameterizedTest(name = "rejects username \"{0}\"")
        @ValueSource(strings = {"ab", "this_username_is_far_too_long_to_be_valid", "has space", "bad-char!", " "})
        void rejectsInvalidUsernames(String username) {
            assertThrows(ValidationException.class,
                    () -> fixtures.authService.register(username, "Passw0rd1", "valid@example.com",
                            "Valid", "Name", null));
        }

        @ParameterizedTest(name = "rejects email \"{0}\"")
        @ValueSource(strings = {"not-an-email", "missing@tld", "@example.com", "spaces in@example.com"})
        void rejectsInvalidEmails(String email) {
            assertThrows(ValidationException.class,
                    () -> fixtures.authService.register("validuser", "Passw0rd1", email, "Valid", "Name", null));
        }

        @ParameterizedTest(name = "rejects password \"{0}\"")
        @ValueSource(strings = {"short1", "alllettersonly", "12345678", ""})
        void rejectsWeakPasswords(String password) {
            assertThrows(ValidationException.class,
                    () -> fixtures.authService.register("validuser", password, "valid@example.com",
                            "Valid", "Name", null));
        }

        @Test
        @DisplayName("rejects a null password")
        void rejectsNullPassword() {
            assertThrows(ValidationException.class,
                    () -> fixtures.authService.register("validuser", null, "valid@example.com",
                            "Valid", "Name", null));
        }

        @ParameterizedTest(name = "rejects name pair \"{0}\" / \"{1}\"")
        @CsvSource({"'','Doe'", "'Jane',''", "'123','Doe'", "'Jane','D@e'"})
        void rejectsInvalidNames(String firstName, String lastName) {
            assertThrows(ValidationException.class,
                    () -> fixtures.authService.register("validuser", "Passw0rd1", "valid@example.com",
                            firstName, lastName, null));
        }

        @Test
        @DisplayName("rejects a malformed phone number")
        void rejectsBadPhone() {
            assertThrows(ValidationException.class,
                    () -> fixtures.authService.register("validuser", "Passw0rd1", "valid@example.com",
                            "Valid", "Name", "abc"));
        }
    }

    @Nested
    @DisplayName("US-02 login")
    class Login {

        @Test
        @DisplayName("authenticates a valid credential pair and records the login time")
        void loginSucceeds() throws BankingException {
            fixtures.authService.register("loginuser", "Passw0rd1", "login@example.com", "Log", "In", null);

            Customer customer = fixtures.authService.login("LoginUser", "Passw0rd1");

            assertEquals("loginuser", customer.getUsername());
            assertNotNull(customer.getLastLoginAt());
        }

        @Test
        @DisplayName("rejects a wrong password with a message that does not reveal which field failed")
        void rejectsWrongPassword() throws BankingException {
            fixtures.authService.register("loginuser", "Passw0rd1", "login@example.com", "Log", "In", null);

            AuthenticationException ex = assertThrows(AuthenticationException.class,
                    () -> fixtures.authService.login("loginuser", "WrongPass1"));
            assertEquals("Invalid username or password.", ex.getMessage());
        }

        @Test
        @DisplayName("rejects an unknown username with the same generic message")
        void rejectsUnknownUsername() {
            AuthenticationException ex = assertThrows(AuthenticationException.class,
                    () -> fixtures.authService.login("ghost", "Passw0rd1"));
            assertEquals("Invalid username or password.", ex.getMessage());
        }

        @Test
        @DisplayName("rejects blank credentials before touching the data access layer")
        void rejectsBlankCredentials() {
            assertThrows(ValidationException.class, () -> fixtures.authService.login("   ", "Passw0rd1"));
            assertThrows(ValidationException.class, () -> fixtures.authService.login("someone", ""));
            assertThrows(ValidationException.class, () -> fixtures.authService.login("someone", null));
        }
    }

    @Nested
    @DisplayName("US-03 profile maintenance")
    class Profile {

        @Test
        @DisplayName("updates the mutable profile fields")
        void updatesProfile() throws BankingException {
            Customer customer = fixtures.registerCustomer("profileuser");

            Customer updated = fixtures.authService.updateProfile(customer.getId(), "new.address@example.com",
                    "Updated", "Name", "555-9999");

            assertEquals("new.address@example.com", updated.getEmail());
            assertEquals("Updated", updated.getFirstName());
            assertEquals("Name", updated.getLastName());
            assertEquals("555-9999", updated.getPhone());
            assertEquals("Updated Name", updated.getFullName().trim());
        }

        @Test
        @DisplayName("allows re-saving the same email on the same account")
        void allowsUnchangedEmail() throws BankingException {
            Customer customer = fixtures.registerCustomer("sameemail");

            Customer updated = fixtures.authService.updateProfile(customer.getId(), customer.getEmail(),
                    "Same", "Email", null);

            assertEquals(customer.getEmail(), updated.getEmail());
            assertNull(updated.getPhone());
        }

        @Test
        @DisplayName("rejects an email already owned by another customer")
        void rejectsEmailOwnedByAnother() throws BankingException {
            fixtures.registerCustomer("owner");
            Customer other = fixtures.registerCustomer("other");

            assertThrows(DuplicateResourceException.class,
                    () -> fixtures.authService.updateProfile(other.getId(), "owner@example.com",
                            "Other", "Person", null));
        }

        @Test
        @DisplayName("rejects an update for an unknown customer id")
        void rejectsUnknownCustomer() {
            assertThrows(ResourceNotFoundException.class,
                    () -> fixtures.authService.updateProfile("00000000-0000-0000-0000-000000000000",
                            "ghost@example.com", "Ghost", "User", null));
            assertThrows(ResourceNotFoundException.class,
                    () -> fixtures.authService.updateProfile(null, "ghost@example.com", "Ghost", "User", null));
            assertThrows(ResourceNotFoundException.class,
                    () -> fixtures.authService.updateProfile("  ", "ghost@example.com", "Ghost", "User", null));
        }

        @Test
        @DisplayName("validates the new field values")
        void validatesUpdatedFields() throws BankingException {
            Customer customer = fixtures.registerCustomer("validateme");

            assertThrows(ValidationException.class, () -> fixtures.authService.updateProfile(customer.getId(),
                    "bad-email", "Valid", "Name", null));
            assertThrows(ValidationException.class, () -> fixtures.authService.updateProfile(customer.getId(),
                    "ok@example.com", "", "Name", null));
        }

        @Test
        @DisplayName("looks a customer up by id")
        void findsById() throws BankingException {
            Customer customer = fixtures.registerCustomer("findme");

            assertEquals(customer.getId(), fixtures.authService.findById(customer.getId()).getId());
            assertThrows(ResourceNotFoundException.class, () -> fixtures.authService.findById("missing"));
        }
    }

    @Nested
    @DisplayName("US-04 password changes")
    class PasswordChange {

        @Test
        @DisplayName("rotates the stored hash when the current password is correct")
        void changesPassword() throws BankingException {
            Customer customer = fixtures.registerCustomer("rotate");
            String originalHash = customer.getPasswordHash();

            fixtures.authService.changePassword(customer.getId(), "Passw0rd!", "BrandNew123");

            Customer reloaded = fixtures.authService.findById(customer.getId());
            assertNotEquals(originalHash, reloaded.getPasswordHash());
            assertTrue(fixtures.passwordEncoder.matches("BrandNew123", reloaded.getPasswordHash()));
            assertNotNull(fixtures.authService.login("rotate", "BrandNew123"));
        }

        @Test
        @DisplayName("rejects the change when the current password is wrong")
        void rejectsWrongCurrentPassword() throws BankingException {
            Customer customer = fixtures.registerCustomer("wrongcurrent");

            assertThrows(AuthenticationException.class,
                    () -> fixtures.authService.changePassword(customer.getId(), "NotMyPass1", "BrandNew123"));
        }

        @Test
        @DisplayName("rejects a weak or unchanged new password")
        void rejectsWeakOrReusedPassword() throws BankingException {
            Customer customer = fixtures.registerCustomer("weaknew");

            assertThrows(ValidationException.class,
                    () -> fixtures.authService.changePassword(customer.getId(), "Passw0rd!", "weak"));
            assertThrows(ValidationException.class,
                    () -> fixtures.authService.changePassword(customer.getId(), "Passw0rd!", "Passw0rd!"));
        }

        @Test
        @DisplayName("rejects the change for an unknown customer")
        void rejectsUnknownCustomer() {
            assertThrows(ResourceNotFoundException.class,
                    () -> fixtures.authService.changePassword("missing", "Passw0rd!", "BrandNew123"));
        }
    }
}
