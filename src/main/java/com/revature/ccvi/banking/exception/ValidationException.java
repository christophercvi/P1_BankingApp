package com.revature.ccvi.banking.exception;

/**
 * Raised when user supplied input fails a format or range rule.
 */
public class ValidationException extends BankingException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
