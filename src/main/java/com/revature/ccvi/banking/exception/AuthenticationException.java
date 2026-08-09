package com.revature.ccvi.banking.exception;

/**
 * Raised for bad credentials or an operation attempted while logged out.
 */
public class AuthenticationException extends BankingException {

    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
