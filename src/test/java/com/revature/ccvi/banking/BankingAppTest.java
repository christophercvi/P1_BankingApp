package com.revature.ccvi.banking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.revature.ccvi.banking.config.AppConfig;
import com.revature.ccvi.banking.model.Account;
import com.revature.ccvi.banking.model.AccountType;
import com.revature.ccvi.banking.model.Customer;
import com.revature.ccvi.banking.service.BankingRules;
import com.revature.ccvi.banking.support.TestFixtures;

/**
 * Verifies that the packaged configuration and the assembled service stack agree with each other,
 * which is what {@code BankingApp.main} relies on when it wires the layers together at startup.
 */
@DisplayName("BankingApp: startup configuration and service wiring")
class BankingAppTest {

    @Test
    @DisplayName("the packaged application.properties supplies everything startup needs")
    void packagedConfigurationIsComplete() {
        AppConfig config = AppConfig.load(AppConfig.DEFAULT_RESOURCE);

        assertNotNull(config.getDatabaseType(), "db.type must resolve to a supported backend");
        assertNotNull(config.get("db.postgres.url"));
        assertNotNull(config.get("db.mongo.uri"));
        assertNotNull(config.get("db.mongo.database"));
        assertNotNull(config.get("app.name"));
        assertTrue(config.getInt("security.argon2.memoryKib", 0) > 0);
        assertTrue(config.getInt("security.argon2.iterations", 0) > 0);
    }

    @Test
    @DisplayName("banking rules built from configuration match the documented defaults")
    void rulesComeFromConfiguration() {
        BankingRules rules = new BankingRules(AppConfig.load(AppConfig.DEFAULT_RESOURCE));

        assertEquals(new BigDecimal("25.00"), rules.getSavingsMinimumBalance());
        assertTrue(rules.getMaxAccountsPerCustomer() > 0);
        assertTrue(rules.getDefaultHistoryPageSize() > 0);
    }

    @Test
    @DisplayName("the wired service stack completes a register-open-deposit flow end to end")
    void wiredServicesHandleACompleteFlow() throws Exception {
        TestFixtures fixtures = new TestFixtures();

        Customer customer = fixtures.authService.register("startup", "Passw0rd1",
                "startup@example.com", "Start", "Up", null);
        Account account = fixtures.accountService.openAccount(customer.getId(),
                AccountType.CHECKING, new BigDecimal("100.00"));
        fixtures.transactionService.deposit(customer.getId(), account.getId(), new BigDecimal("50.00"), "seed");

        assertEquals(new BigDecimal("150.00"),
                fixtures.accountService.getBalance(customer.getId(), account.getId()));
        assertEquals(2, fixtures.transactionService.getHistory(customer.getId(), account.getId(), null).size());
        assertNotNull(fixtures.authService.login("startup", "Passw0rd1"));
    }
}
