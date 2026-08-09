package com.revature.ccvi.banking;

import com.revature.ccvi.banking.config.AppConfig;
import com.revature.ccvi.banking.config.DaoFactory;
import com.revature.ccvi.banking.config.DaoFactoryProvider;
import com.revature.ccvi.banking.exception.DataAccessException;
import com.revature.ccvi.banking.service.AccountService;
import com.revature.ccvi.banking.service.AuthService;
import com.revature.ccvi.banking.service.BankingRules;
import com.revature.ccvi.banking.service.Password4jEncoder;
import com.revature.ccvi.banking.service.PasswordEncoder;
import com.revature.ccvi.banking.service.TransactionService;
import com.revature.ccvi.banking.ui.ConsoleIO;
import com.revature.ccvi.banking.ui.MenuRouter;

/**
 * Application entry point. This class is the only place that wires the layers together: it reads
 * configuration, asks the provider for a DAO set, injects those DAOs into the services, and hands
 * the services to the console. Switching {@code db.type} between {@code postgres} and
 * {@code mongo} changes nothing below this method.
 */
public class BankingApp {

    public static void main(String[] args) {
        ConsoleIO console = new ConsoleIO();
        AppConfig config = AppConfig.getInstance();

        try (DaoFactory daoFactory = DaoFactoryProvider.create(config)) {
            if (config.getBoolean("app.schema.autoInitialize", true)) {
                daoFactory.initializeSchema();
            }

            PasswordEncoder passwordEncoder = new Password4jEncoder(config);
            BankingRules rules = new BankingRules(config);

            AuthService authService = new AuthService(daoFactory.customerDao(), passwordEncoder);
            AccountService accountService = new AccountService(
                    daoFactory.accountDao(), daoFactory.transactionDao(), rules);
            TransactionService transactionService = new TransactionService(
                    daoFactory.accountDao(), daoFactory.transactionDao(), daoFactory.transferExecutor(), rules);

            new MenuRouter(console, authService, accountService, transactionService,
                    daoFactory.getDatabaseType(), config.get("app.name", "CCVI Community Bank")).run();
        } catch (DataAccessException ex) {
            console.error(ex.getMessage());
            console.info("Check the db.* settings in src/main/resources/application.properties "
                    + "and confirm the database server is running.");
            System.exit(1);
        } catch (IllegalStateException ex) {
            console.error("Configuration problem: " + ex.getMessage());
            System.exit(2);
        } catch (RuntimeException ex) {
            console.error("Unexpected error: " + ex.getMessage());
            System.exit(3);
        }
    }
}
