package ai.motoria.common.persistence;

import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniqueViolationsTest {

    @Test
    void hibernateUniqueViolationIsDetected() {
        ConstraintViolationException violation = new ConstraintViolationException(
                "duplicate", new SQLException("duplicate key", "23505"), "vehicle_country_plate_key");

        assertTrue(UniqueViolations.isUniqueViolation(violation));
    }

    @Test
    void wrappedUniqueViolationIsDetected() {
        ConstraintViolationException violation = new ConstraintViolationException(
                "duplicate", new SQLException("duplicate key", "23505"), "user_profile_pkey");

        assertTrue(UniqueViolations.isUniqueViolation(new PersistenceException("flush failed", violation)));
        assertTrue(UniqueViolations.isUniqueViolation(
                new RuntimeException("outer", new RuntimeException(new SQLException("duplicate key", "23505")))));
    }

    @Test
    void otherConstraintViolationsAreNotUnique() {
        ConstraintViolationException notNull = new ConstraintViolationException(
                "null value", new SQLException("null value", "23502"), "listing_title_not_null");
        ConstraintViolationException foreignKey = new ConstraintViolationException(
                "fk", new SQLException("fk", "23503"), "consent_user_fk");

        assertFalse(UniqueViolations.isUniqueViolation(notNull));
        assertFalse(UniqueViolations.isUniqueViolation(new PersistenceException(foreignKey)));
    }

    @Test
    void unrelatedExceptionsAreNotUnique() {
        assertFalse(UniqueViolations.isUniqueViolation(new IllegalStateException("boom")));
        assertFalse(UniqueViolations.isUniqueViolation(new PersistenceException("timeout", new SQLException("t", "57014"))));
        assertFalse(UniqueViolations.isUniqueViolation(null));
    }
}
