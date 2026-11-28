package ai.motoria.common.persistence;

import org.hibernate.exception.ConstraintViolationException;

import java.sql.SQLException;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Detects a unique-constraint violation anywhere in an exception chain (PostgreSQL SQLState 23505).
 * Used by the concurrent-creation pattern: create in its own transaction, and on a duplicate re-read the winner.
 */
public final class UniqueViolations {

    static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private UniqueViolations() {
    }

    public static boolean isUniqueViolation(Throwable throwable) {
        Map<Throwable, Boolean> seen = new IdentityHashMap<>();
        Throwable current = throwable;
        while (current != null && seen.put(current, Boolean.TRUE) == null) {
            if (current instanceof ConstraintViolationException violation) {
                String sqlState = violation.getSQLState();
                if (sqlState == null || UNIQUE_VIOLATION_SQL_STATE.equals(sqlState)) {
                    return true;
                }
            } else if (current instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
