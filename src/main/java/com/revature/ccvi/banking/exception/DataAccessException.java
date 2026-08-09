package com.revature.ccvi.banking.exception;

/**
 * Unchecked wrapper around driver level failures (SQLException, MongoException). Keeping it
 * unchecked lets DAO signatures stay database agnostic while the console still reports the
 * problem instead of terminating.
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
