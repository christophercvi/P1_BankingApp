package com.revature.ccvi.banking.dao.postgres;

import java.sql.SQLException;

/**
 * Translates PostgreSQL SQLSTATE codes into intent so DAO code does not repeat magic strings.
 */
final class SqlErrors {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";

    private SqlErrors() {
    }

    static boolean isUniqueViolation(SQLException ex) {
        return matches(ex, UNIQUE_VIOLATION);
    }

    static boolean isForeignKeyViolation(SQLException ex) {
        return matches(ex, FOREIGN_KEY_VIOLATION);
    }

    static boolean isCheckViolation(SQLException ex) {
        return matches(ex, CHECK_VIOLATION);
    }

    private static boolean matches(SQLException ex, String sqlState) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException && sqlState.equals(sqlException.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
