package com.revature.ccvi.banking.exception;

/**
 * Raised when a debit would overdraw an account or break its minimum balance.
 */
public class InsufficientFundsException extends BankingException {

    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String message) {
        super(message);
    }

    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }
}
