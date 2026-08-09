package com.revature.ccvi.banking.exception;

/**
 * Raised when an account's status forbids the requested operation.
 */
public class InvalidAccountStateException extends BankingException {

    private static final long serialVersionUID = 1L;

    public InvalidAccountStateException(String message) {
        super(message);
    }

    public InvalidAccountStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
