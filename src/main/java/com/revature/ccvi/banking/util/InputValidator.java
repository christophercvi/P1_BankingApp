package com.revature.ccvi.banking.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import com.revature.ccvi.banking.exception.ValidationException;

/**
 * Single place where every field level rule lives. Services call these before touching a DAO
 * so invalid data never reaches the database, and the console reuses the same messages.
 */
public final class InputValidator {

    public static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("1000000.00");

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_]{3,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9(][0-9\\-() ]{6,19}$");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z'\\- ]{0,59}$");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[0-9]{10}$");
    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*[0-9].*");

    private InputValidator() {
    }

    public static String requireText(String value, String fieldName) throws ValidationException {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required.");
        }
        return value.trim();
    }

    public static String validateUsername(String username) throws ValidationException {
        String trimmed = requireText(username, "Username");
        if (!USERNAME.matcher(trimmed).matches()) {
            throw new ValidationException(
                    "Username must be 3-30 characters using letters, digits, or underscores only.");
        }
        return trimmed.toLowerCase();
    }

    public static String validateEmail(String email) throws ValidationException {
        String trimmed = requireText(email, "Email");
        if (trimmed.length() > 120 || !EMAIL.matcher(trimmed).matches()) {
            throw new ValidationException("Email must be a valid address such as name@example.com.");
        }
        return trimmed.toLowerCase();
    }

    public static String validateName(String name, String fieldName) throws ValidationException {
        String trimmed = requireText(name, fieldName);
        if (!NAME.matcher(trimmed).matches()) {
            throw new ValidationException(fieldName + " must contain letters, spaces, hyphens, or apostrophes only.");
        }
        return trimmed;
    }

    /** Phone is optional; blank input normalizes to null rather than failing. */
    public static String validateOptionalPhone(String phone) throws ValidationException {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String trimmed = phone.trim();
        if (!PHONE.matcher(trimmed).matches()) {
            throw new ValidationException("Phone must be 7-20 characters of digits, spaces, hyphens, or parentheses.");
        }
        return trimmed;
    }

    public static void validatePassword(String password) throws ValidationException {
        if (password == null || password.isBlank()) {
            throw new ValidationException("Password is required.");
        }
        if (password.length() < 8 || password.length() > 72) {
            throw new ValidationException("Password must be between 8 and 72 characters.");
        }
        if (!HAS_LETTER.matcher(password).matches() || !HAS_DIGIT.matcher(password).matches()) {
            throw new ValidationException("Password must contain at least one letter and one digit.");
        }
    }

    public static String validateAccountNumber(String accountNumber) throws ValidationException {
        String trimmed = requireText(accountNumber, "Account number");
        if (!ACCOUNT_NUMBER.matcher(trimmed).matches()) {
            throw new ValidationException("Account number must be exactly 10 digits.");
        }
        return trimmed;
    }

    public static BigDecimal validateAmount(BigDecimal amount) throws ValidationException {
        if (amount == null) {
            throw new ValidationException("Amount is required.");
        }
        if (!MoneyUtil.isPositive(amount)) {
            throw new ValidationException("Amount must be greater than zero.");
        }
        if (!MoneyUtil.hasValidScale(amount)) {
            throw new ValidationException("Amount cannot have more than two decimal places.");
        }
        if (amount.compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
            throw new ValidationException("Amount cannot exceed " + MoneyUtil.format(MAX_TRANSACTION_AMOUNT)
                    + " in a single transaction.");
        }
        return MoneyUtil.normalize(amount);
    }

    public static BigDecimal parseAmount(String raw) throws ValidationException {
        String trimmed = requireText(raw, "Amount").replace(",", "").replace("$", "");
        try {
            return validateAmount(new BigDecimal(trimmed));
        } catch (NumberFormatException ex) {
            throw new ValidationException("Amount must be numeric, for example 250.00.");
        }
    }

    public static LocalDate parseDate(String raw) throws ValidationException {
        String trimmed = requireText(raw, "Date");
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ex) {
            throw new ValidationException("Date must use the yyyy-MM-dd format, for example 2026-01-31.");
        }
    }

    public static String validateDescription(String description) throws ValidationException {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > 255) {
            throw new ValidationException("Description cannot exceed 255 characters.");
        }
        return trimmed;
    }
}
