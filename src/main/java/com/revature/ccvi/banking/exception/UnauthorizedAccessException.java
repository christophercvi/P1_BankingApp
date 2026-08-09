package com.revature.ccvi.banking.exception;

/**
 * Raised when the logged in customer does not own the target account.
 */
public class UnauthorizedAccessException extends BankingException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedAccessException(String message) {
        super(message);
    }

    public UnauthorizedAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
