package com.revature.ccvi.banking.exception;

/**
 * Raised when the input stream is exhausted while a prompt is waiting for an answer.
 *
 * <p>This is deliberately separate from {@link ValidationException}. A validation failure means the
 * customer typed something unusable and should be re-prompted; end of input means there is nobody
 * left to prompt, so the surrounding menu loop must unwind and let the application shut down. Both
 * cases used to share one exception type, which made every menu loop spin forever once stdin
 * closed.</p>
 */
public class EndOfInputException extends BankingException {

    private static final long serialVersionUID = 1L;

    public EndOfInputException() {
        super("Input ended unexpectedly.");
    }

    public EndOfInputException(String message) {
        super(message);
    }
}
