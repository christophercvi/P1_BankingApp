package com.revature.ccvi.banking.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.exception.EndOfInputException;
import com.revature.ccvi.banking.exception.ValidationException;

@DisplayName("ConsoleIO: prompt reading, input validation, and output helpers")
class ConsoleIOTest {

    private ByteArrayOutputStream output;

    private ConsoleIO consoleWith(String input) {
        output = new ByteArrayOutputStream();
        return new ConsoleIO(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    private String captured() {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("reads a line and echoes the prompt")
    void readsLine() {
        ConsoleIO console = consoleWith("hello\n");

        assertEquals("hello", console.readLine("Prompt: "));
        assertTrue(captured().contains("Prompt: "));
    }

    @Test
    @DisplayName("returns null at end of input so callers can exit cleanly")
    void returnsNullAtEndOfInput() {
        assertNull(consoleWith("").readLine("Prompt: "));
    }

    @Test
    @DisplayName("trims required input and rejects a blank value")
    void readsRequiredValue() throws BankingException {
        assertEquals("value", consoleWith("  value  \n").readRequired("Prompt: ", "Field"));
        assertThrows(ValidationException.class,
                () -> consoleWith("   \n").readRequired("Prompt: ", "Field"));
        assertThrows(EndOfInputException.class,
                () -> consoleWith("").readRequired("Prompt: ", "Field"));
    }

    @Test
    @DisplayName("treats blank optional input as absent")
    void readsOptionalValue() {
        assertEquals("value", consoleWith("value\n").readOptional("Prompt: "));
        assertNull(consoleWith("   \n").readOptional("Prompt: "));
        assertNull(consoleWith("").readOptional("Prompt: "));
    }

    @Test
    @DisplayName("reads a password from the stream when no terminal is attached")
    void readsPassword() throws BankingException {
        assertEquals("Secret123", consoleWith("Secret123\n").readPassword("Password: "));
        assertThrows(ValidationException.class, () -> consoleWith("\n").readPassword("Password: "));
        assertThrows(EndOfInputException.class, () -> consoleWith("").readPassword("Password: "));
    }

    @ParameterizedTest(name = "accepts menu choice \"{0}\" as {1}")
    @CsvSource({"'1',1", "' 3 ',3", "'5',5"})
    void readsMenuChoice(String input, int expected) throws BankingException {
        assertEquals(expected, consoleWith(input + "\n").readMenuChoice("Choice: ", 1, 5));
    }

    @ParameterizedTest(name = "rejects menu choice \"{0}\"")
    @ValueSource(strings = {"0", "6", "abc", "", "  "})
    void rejectsBadMenuChoice(String input) {
        assertThrows(ValidationException.class,
                () -> consoleWith(input + "\n").readMenuChoice("Choice: ", 1, 5));
    }

    @Test
    @DisplayName("signals end of input distinctly so menu loops can unwind instead of re-prompting")
    void endOfInputIsDistinctFromInvalidInput() {
        assertThrows(EndOfInputException.class, () -> consoleWith("").readMenuChoice("Choice: ", 1, 5));
        // A blank line is recoverable; a closed stream is not.
        assertThrows(ValidationException.class, () -> consoleWith("\n").readMenuChoice("Choice: ", 1, 5));
    }

    @Test
    @DisplayName("parses a currency amount, tolerating a dollar sign and separators")
    void readsAmount() throws ValidationException {
        assertEquals(new BigDecimal("1250.75"), consoleWith("$1,250.75\n").readAmount("Amount: "));
        assertThrows(ValidationException.class, () -> consoleWith("abc\n").readAmount("Amount: "));
    }

    @Test
    @DisplayName("parses an ISO date")
    void readsDate() throws ValidationException {
        assertEquals(LocalDate.of(2026, 3, 15), consoleWith("2026-03-15\n").readDate("Date: "));
        assertThrows(ValidationException.class, () -> consoleWith("15/03/2026\n").readDate("Date: "));
    }

    @ParameterizedTest(name = "\"{0}\" is affirmative: {1}")
    @CsvSource({"y,true", "Y,true", "yes,true", " YES ,true", "n,false", "no,false", "'',false", "maybe,false"})
    void readsYesNo(String input, boolean expected) {
        assertEquals(expected, consoleWith(input + "\n").readYesNo("Continue? "));
    }

    @Test
    @DisplayName("treats end of input as a negative answer")
    void yesNoAtEndOfInput() {
        assertFalse(consoleWith("").readYesNo("Continue? "));
    }

    @Test
    @DisplayName("renders headings, status lines, and blank spacing")
    void rendersOutputHelpers() {
        ConsoleIO console = consoleWith("\n");

        console.heading("Account Management");
        console.success("Saved");
        console.error("Something failed");
        console.info("Detail line");
        console.println("plain");
        console.print("no newline");
        console.blank();
        console.pause();

        String text = captured();
        assertTrue(text.contains("Account Management"));
        assertTrue(text.contains("===="));
        assertTrue(text.contains("[OK] Saved"));
        assertTrue(text.contains("[!] Something failed"));
        assertTrue(text.contains("    Detail line"));
        assertTrue(text.contains("plain"));
        assertTrue(text.contains("no newline"));
        assertTrue(text.contains("Press Enter to continue"));
    }
}
