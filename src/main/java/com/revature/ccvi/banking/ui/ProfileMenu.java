package com.revature.ccvi.banking.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.revature.ccvi.banking.exception.BankingException;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.exception.EndOfInputException;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.service.AuthService;

/**
 * Profile and credential screens (US-03 and US-04).
 */
class ProfileMenu {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ConsoleIO console;
    private final AuthService authService;
    private final Session session;

    ProfileMenu(ConsoleIO console, AuthService authService, Session session) {
        this.console = console;
        this.authService = authService;
        this.session = session;
    }

    void show() throws EndOfInputException {
        boolean running = true;
        while (running) {
            console.heading("Profile");
            console.println("  1) View profile");
            console.println("  2) Update profile");
            console.println("  3) Change password");
            console.println("  4) Back to main menu");
            try {
                switch (console.readMenuChoice("\nSelect an option [1-4]: ", 1, 4)) {
                    case 1 -> viewProfile();
                    case 2 -> updateProfile();
                    case 3 -> changePassword();
                    default -> running = false;
                }
            } catch (EndOfInputException ex) {
                throw ex;
            } catch (BankingException ex) {
                console.error(ex.getMessage());
            } catch (DataAccessException ex) {
                console.error("Database error: " + ex.getMessage());
            }
        }
    }

    private void viewProfile() throws BankingException {
        Customer customer = authService.findById(session.getCustomerId());
        console.heading("Your Profile");
        console.info("Username:   " + customer.getUsername());
        console.info("Name:       " + customer.getFullName().trim());
        console.info("Email:      " + customer.getEmail());
        console.info("Phone:      " + (customer.getPhone() == null ? "not provided" : customer.getPhone()));
        console.info("Registered: " + (customer.getCreatedAt() == null ? "-" : TIMESTAMP.format(customer.getCreatedAt())));
        console.info("Last login: " + (customer.getLastLoginAt() == null ? "-" : TIMESTAMP.format(customer.getLastLoginAt())));
        console.pause();
    }

    private void updateProfile() throws BankingException {
        Customer current = authService.findById(session.getCustomerId());
        console.heading("Update Profile");
        console.info("Press Enter to keep the current value shown in brackets.");
        String email = valueOrDefault(console.readOptional("Email [" + current.getEmail() + "]: "), current.getEmail());
        String firstName = valueOrDefault(console.readOptional("First name [" + current.getFirstName() + "]: "),
                current.getFirstName());
        String lastName = valueOrDefault(console.readOptional("Last name [" + current.getLastName() + "]: "),
                current.getLastName());
        String phoneDisplay = current.getPhone() == null ? "none" : current.getPhone();
        String phoneInput = console.readOptional("Phone [" + phoneDisplay + "]: ");
        String phone = phoneInput == null ? current.getPhone() : phoneInput;

        Customer updated = authService.updateProfile(session.getCustomerId(), email, firstName, lastName, phone);
        session.login(updated);
        console.success("Profile updated.");
        console.pause();
    }

    private void changePassword() throws BankingException {
        console.heading("Change Password");
        String currentPassword = console.readPassword("Current password: ");
        String newPassword = console.readPassword("New password (min 8 chars, letters and digits): ");
        String confirmation = console.readPassword("Confirm new password: ");
        if (!newPassword.equals(confirmation)) {
            console.error("The new passwords do not match.");
            return;
        }
        authService.changePassword(session.getCustomerId(), currentPassword, newPassword);
        console.success("Password changed. Use your new password next time you sign in.");
        console.pause();
    }

    private String valueOrDefault(String input, String fallback) {
        return input == null ? fallback : input;
    }
}
