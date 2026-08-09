package com.revature.ccvi.banking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.revature.ccvi.banking.exception.ValidationException;

@DisplayName("InputValidator and MoneyUtil: field level rules")
class InputValidatorTest {

    @ParameterizedTest(name = "accepts username \"{0}\" as \"{1}\"")
    @CsvSource({"abc,abc", "Jane_Doe,jane_doe", "  spaced  ,spaced", "user123,user123"})
    void acceptsValidUsernames(String input, String expected) throws ValidationException {
        assertEquals(expected, InputValidator.validateUsername(input));
    }

    @ParameterizedTest(name = "rejects username \"{0}\"")
    @ValueSource(strings = {"ab", "has space", "dash-not-allowed", "way_too_long_username_value_here_x"})
    void rejectsInvalidUsernames(String input) {
        assertThrows(ValidationException.class, () -> InputValidator.validateUsername(input));
    }

    @Test
    @DisplayName("rejects a null or blank required field with a field specific message")
    void rejectsBlankRequiredField() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> InputValidator.requireText("   ", "Nickname"));
        assertTrue(ex.getMessage().startsWith("Nickname"));
        assertThrows(ValidationException.class, () -> InputValidator.requireText(null, "Nickname"));
    }

    @ParameterizedTest(name = "accepts email \"{0}\"")
    @ValueSource(strings = {"a@b.co", "first.last+tag@sub.example.org", "USER@EXAMPLE.COM"})
    void acceptsValidEmails(String input) throws ValidationException {
        assertEquals(input.toLowerCase(), InputValidator.validateEmail(input));
    }

    @ParameterizedTest(name = "rejects email \"{0}\"")
    @ValueSource(strings = {"plain", "no@tld", "@example.com", "a b@example.com", "trailing@example."})
    void rejectsInvalidEmails(String input) {
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail(input));
    }

    @Test
    @DisplayName("rejects an email that exceeds the column length")
    void rejectsOverlongEmail() {
        String local = "x".repeat(120);
        assertThrows(ValidationException.class, () -> InputValidator.validateEmail(local + "@example.com"));
    }

    @ParameterizedTest(name = "accepts name \"{0}\"")
    @ValueSource(strings = {"Jane", "Mary Jane", "O'Brien", "Smith-Jones"})
    void acceptsValidNames(String input) throws ValidationException {
        assertEquals(input, InputValidator.validateName(input, "First name"));
    }

    @ParameterizedTest(name = "rejects name \"{0}\"")
    @ValueSource(strings = {"123", "J@ne", "", "  "})
    void rejectsInvalidNames(String input) {
        assertThrows(ValidationException.class, () -> InputValidator.validateName(input, "First name"));
    }

    @ParameterizedTest(name = "accepts phone \"{0}\"")
    @ValueSource(strings = {"5551234567", "555-123-4567", "(555) 123-4567", "+15551234567"})
    void acceptsValidPhones(String input) throws ValidationException {
        assertEquals(input, InputValidator.validateOptionalPhone(input));
    }

    @Test
    @DisplayName("treats a blank phone as absent")
    void blankPhoneBecomesNull() throws ValidationException {
        assertNull(InputValidator.validateOptionalPhone(null));
        assertNull(InputValidator.validateOptionalPhone("   "));
    }

    @ParameterizedTest(name = "rejects phone \"{0}\"")
    @ValueSource(strings = {"12345", "abcdefg", "555_123_4567"})
    void rejectsInvalidPhones(String input) {
        assertThrows(ValidationException.class, () -> InputValidator.validateOptionalPhone(input));
    }

    @ParameterizedTest(name = "accepts password of length {0}")
    @ValueSource(strings = {"Passw0rd", "a1234567", "Very Long Passphrase 2026"})
    void acceptsValidPasswords(String input) throws ValidationException {
        InputValidator.validatePassword(input);
    }

    @Test
    @DisplayName("rejects passwords that are short, blank, or missing a character class")
    void rejectsWeakPasswords() {
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword(null));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("   "));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("Pass1"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("onlyletters"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("12345678"));
        assertThrows(ValidationException.class, () -> InputValidator.validatePassword("a1".repeat(40)));
    }

    @Test
    @DisplayName("accepts a ten digit account number and rejects anything else")
    void validatesAccountNumbers() throws ValidationException {
        assertEquals("1234567890", InputValidator.validateAccountNumber(" 1234567890 "));
        assertThrows(ValidationException.class, () -> InputValidator.validateAccountNumber("123456789"));
        assertThrows(ValidationException.class, () -> InputValidator.validateAccountNumber("12345678901"));
        assertThrows(ValidationException.class, () -> InputValidator.validateAccountNumber("12345abcde"));
        assertThrows(ValidationException.class, () -> InputValidator.validateAccountNumber(null));
    }

    @ParameterizedTest(name = "normalizes amount \"{0}\" to {1}")
    @CsvSource({"0.10,0.10", "5,5.00", "1000000,1000000.00", "12.30,12.30"})
    void normalizesValidAmounts(String input, String expected) throws ValidationException {
        assertEquals(new BigDecimal(expected), InputValidator.validateAmount(new BigDecimal(input)));
    }

    @ParameterizedTest(name = "rejects amount \"{0}\"")
    @ValueSource(strings = {"0", "-1", "0.001", "1000000.01"})
    void rejectsInvalidAmounts(String input) {
        assertThrows(ValidationException.class, () -> InputValidator.validateAmount(new BigDecimal(input)));
    }

    @Test
    @DisplayName("rejects a null amount")
    void rejectsNullAmount() {
        assertThrows(ValidationException.class, () -> InputValidator.validateAmount(null));
    }

    @ParameterizedTest(name = "parses amount text \"{0}\"")
    @CsvSource({"'100','100.00'", "'$250.50','250.50'", "'1,000.00','1000.00'", "'  42  ','42.00'"})
    void parsesAmountText(String input, String expected) throws ValidationException {
        assertEquals(new BigDecimal(expected), InputValidator.parseAmount(input));
    }

    @ParameterizedTest(name = "rejects amount text \"{0}\"")
    @ValueSource(strings = {"abc", "12.3.4", "", "   "})
    void rejectsBadAmountText(String input) {
        assertThrows(ValidationException.class, () -> InputValidator.parseAmount(input));
    }

    @Test
    @DisplayName("parses an ISO date and rejects other formats")
    void parsesDates() throws ValidationException {
        assertEquals(LocalDate.of(2026, 1, 31), InputValidator.parseDate("2026-01-31"));
        assertThrows(ValidationException.class, () -> InputValidator.parseDate("01/31/2026"));
        assertThrows(ValidationException.class, () -> InputValidator.parseDate("2026-13-01"));
        assertThrows(ValidationException.class, () -> InputValidator.parseDate(null));
    }

    @Test
    @DisplayName("normalizes descriptions and enforces the length cap")
    void validatesDescriptions() throws ValidationException {
        assertNull(InputValidator.validateDescription(null));
        assertNull(InputValidator.validateDescription("   "));
        assertEquals("memo", InputValidator.validateDescription("  memo  "));
        assertThrows(ValidationException.class, () -> InputValidator.validateDescription("x".repeat(256)));
    }

    @Test
    @DisplayName("MoneyUtil keeps two decimal places and formats currency")
    void moneyUtilBehaviour() {
        assertEquals(new BigDecimal("10.00"), MoneyUtil.normalize(new BigDecimal("10")));
        assertEquals(new BigDecimal("10.01"), MoneyUtil.normalize(new BigDecimal("10.005")));
        assertNull(MoneyUtil.normalize(null));
        assertEquals(new BigDecimal("0.00"), MoneyUtil.zero());
        assertTrue(MoneyUtil.isPositive(new BigDecimal("0.01")));
        assertFalse(MoneyUtil.isPositive(BigDecimal.ZERO));
        assertFalse(MoneyUtil.isPositive(null));
        assertTrue(MoneyUtil.hasValidScale(new BigDecimal("1.23")));
        assertFalse(MoneyUtil.hasValidScale(new BigDecimal("1.234")));
        assertFalse(MoneyUtil.hasValidScale(null));
        assertEquals("$1,234.50", MoneyUtil.format(new BigDecimal("1234.5")));
        assertEquals("$0.00", MoneyUtil.format(null));
    }
}
