package com.revature.ccvi.banking.exception;

/**
 * Raised when a requested customer, account, or transaction does not exist.
 */
public class ResourceNotFoundException extends BankingException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
