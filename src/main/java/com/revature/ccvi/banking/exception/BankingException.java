package com.revature.ccvi.banking.exception;

/**
 * Base type for every recoverable business failure. The console layer catches this once
 * and renders {@link #getMessage()} directly, so all subclasses carry user-facing text.
 */
public class BankingException extends Exception {

    private static final long serialVersionUID = 1L;

    public BankingException(String message) {
        super(message);
    }

    public BankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
