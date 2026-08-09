package com.revature.ccvi.banking.exception;

/**
 * Raised when a unique constraint (username, email, account number) is violated.
 */
public class DuplicateResourceException extends BankingException {

    private static final long serialVersionUID = 1L;

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
