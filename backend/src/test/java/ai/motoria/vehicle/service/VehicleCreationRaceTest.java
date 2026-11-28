package ai.motoria.vehicle.service;

import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.exception.VehicleErrorCodes;
import ai.motoria.vehicle.repository.VehicleRepository;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two sellers register the same plate at the same time: the loser re-reads the vehicle instead of returning 500.
 */
class VehicleCreationRaceTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final VehicleCreator vehicleCreator = mock(VehicleCreator.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final UUID requesterId = UUID.randomUUID();
    private VehicleService service;

    @BeforeEach
    void setUp() {
        service = new VehicleService();
        service.vehicleRepository = vehicleRepository;
        service.vehicleCreator = vehicleCreator;
        service.currentUser = currentUser;
        when(currentUser.id()).thenReturn(requesterId);
    }

    @Test
    void createdVehicleIsReturned() {
        Vehicle created = Vehicle.register("CR", "ABC123", UUID.randomUUID());
        when(vehicleCreator.create("CR", "ABC123", requesterId)).thenReturn(created.getId());
        when(vehicleRepository.findByIdOptional(created.getId())).thenReturn(Optional.of(created));

        assertSame(created, service.createOrLoad("CR", "ABC123"));
    }

    @Test
    void duplicatePlateReReadsTheWinner() {
        Vehicle winner = Vehicle.register("CR", "ABC123", UUID.randomUUID());
        when(vehicleCreator.create("CR", "ABC123", requesterId)).thenThrow(duplicate());
        when(vehicleRepository.findByCountryAndPlate("CR", "ABC123")).thenReturn(Optional.of(winner));

        assertSame(winner, service.createOrLoad("CR", "ABC123"));
    }

    @Test
    void duplicateWithoutWinnerReturnsStableCode() {
        when(vehicleCreator.create("CR", "ABC123", requesterId)).thenThrow(duplicate());
        when(vehicleRepository.findByCountryAndPlate("CR", "ABC123")).thenReturn(Optional.empty());

        InvalidStateException exception = assertThrows(InvalidStateException.class,
                () -> service.createOrLoad("CR", "ABC123"));
        assertEquals(VehicleErrorCodes.VEHICLE_CONCURRENT_CREATION, exception.getCode());
    }

    @Test
    void otherPersistenceErrorsAreRethrown() {
        PersistenceException failure = new PersistenceException("connection lost", new SQLException("lost", "08006"));
        when(vehicleCreator.create("CR", "ABC123", requesterId)).thenThrow(failure);

        assertSame(failure, assertThrows(PersistenceException.class, () -> service.createOrLoad("CR", "ABC123")));
    }

    private PersistenceException duplicate() {
        return new PersistenceException("flush failed", new ConstraintViolationException(
                "duplicate", new SQLException("duplicate key", "23505"), "vehicle_country_plate_key"));
    }
}
