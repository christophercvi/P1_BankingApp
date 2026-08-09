package com.revature.ccvi.banking.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.NoSuchElementException;

import com.revature.ccvi.banking.exception.EndOfInputException;
import com.revature.ccvi.banking.exception.ValidationException;
import com.revature.ccvi.banking.util.InputValidator;

/**
 * Thin, injectable console boundary. Every read and write in the presentation layer goes through
 * this class, which is what lets the menus be driven by scripted input in tests.
 *
 * <p>When a real terminal is attached, password prompts use {@link System#console()} so the
 * characters are not echoed.</p>
 */
public class ConsoleIO {

    private final BufferedReader reader;
    private final PrintStream out;
    private final boolean maskPasswords;

    public ConsoleIO() {
        this(System.in, System.out, System.console() != null);
    }

    public ConsoleIO(InputStream in, PrintStream out) {
        this(in, out, false);
    }

    public ConsoleIO(InputStream in, PrintStream out, boolean maskPasswords) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.out = out;
        this.maskPasswords = maskPasswords;
    }

    public void print(String text) {
        out.print(text);
        out.flush();
    }

    public void println(String text) {
        out.println(text);
    }

    public void blank() {
        out.println();
    }

    public void heading(String title) {
        String border = "=".repeat(Math.max(title.length() + 4, 46));
        out.println();
        out.println(border);
        out.println("  " + title);
        out.println(border);
    }

    public void success(String message) {
        out.println("[OK] " + message);
    }

    public void error(String message) {
        out.println("[!] " + message);
    }

    public void info(String message) {
        out.println("    " + message);
    }

    /** Returns null at end of input, which the menus treat as an exit signal. */
    public String readLine(String prompt) {
        print(prompt);
        try {
            return reader.readLine();
        } catch (IOException | NoSuchElementException ex) {
            return null;
        }
    }

    public String readRequired(String prompt, String fieldName)
            throws ValidationException, EndOfInputException {
        String value = readLine(prompt);
        if (value == null) {
            throw new EndOfInputException();
        }
        return InputValidator.requireText(value, fieldName);
    }

    public String readOptional(String prompt) {
        String value = readLine(prompt);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String readPassword(String prompt) throws ValidationException, EndOfInputException {
        if (maskPasswords && System.console() != null) {
            char[] characters = System.console().readPassword(prompt);
            if (characters == null || characters.length == 0) {
                throw new ValidationException("Password is required.");
            }
            return new String(characters);
        }
        String value = readLine(prompt);
        if (value == null) {
            throw new EndOfInputException();
        }
        if (value.isEmpty()) {
            throw new ValidationException("Password is required.");
        }
        return value;
    }

    public int readMenuChoice(String prompt, int min, int max)
            throws ValidationException, EndOfInputException {
        String raw = readLine(prompt);
        if (raw == null) {
            throw new EndOfInputException();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("Please enter a number between " + min + " and " + max + ".");
        }
        try {
            int choice = Integer.parseInt(trimmed);
            if (choice < min || choice > max) {
                throw new ValidationException("Please choose an option between " + min + " and " + max + ".");
            }
            return choice;
        } catch (NumberFormatException ex) {
            throw new ValidationException("'" + trimmed + "' is not a valid menu option.");
        }
    }

    public BigDecimal readAmount(String prompt) throws ValidationException {
        return InputValidator.parseAmount(readLine(prompt));
    }

    public LocalDate readDate(String prompt) throws ValidationException {
        return InputValidator.parseDate(readLine(prompt));
    }

    public boolean readYesNo(String prompt) {
        String value = readLine(prompt);
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.equals("y") || normalized.equals("yes");
    }

    public void pause() {
        readLine("\nPress Enter to continue...");
    }
}
