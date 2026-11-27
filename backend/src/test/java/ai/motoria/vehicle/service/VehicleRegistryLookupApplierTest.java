package ai.motoria.vehicle.service;

import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.port.UserIdentityPort;
import ai.motoria.common.port.UserIdentitySnapshot;
import ai.motoria.vehicle.domain.RegistryAnomaly;
import ai.motoria.vehicle.domain.RegistryLookup;
import ai.motoria.vehicle.domain.RegistryLookupOutcome;
import ai.motoria.vehicle.domain.RegistryLookupPurpose;
import ai.motoria.vehicle.domain.RegistryLookupStatus;
import ai.motoria.vehicle.domain.RegistrySource;
import ai.motoria.vehicle.domain.ReviewReason;
import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.domain.VehicleAttributeKey;
import ai.motoria.vehicle.domain.VehicleStatus;
import ai.motoria.vehicle.event.VehicleEventProducer;
import ai.motoria.vehicle.integration.VehicleRegistryRecord;
import ai.motoria.vehicle.repository.RegistryLookupRepository;
import ai.motoria.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registry agreement answers applied to the vehicle: automatic approval, review, not found, reuse within 90 days,
 * daily quota and anomalies against the seller. Fictitious data.
 */
class VehicleRegistryLookupApplierTest {

    private static final String SELLER_HASH = "seller-hash";

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final RegistryLookupRepository registryLookupRepository = mock(RegistryLookupRepository.class);
    private final VehicleEventProducer vehicleEventProducer = mock(VehicleEventProducer.class);
    private final UserIdentityPort userIdentityPort = mock(UserIdentityPort.class);
    private VehicleRegistryLookupApplier applier;
    private VehicleRegistrySettings settings;
    private Vehicle vehicle;
    private UUID seller;

    @BeforeEach
    void setUp() {
        settings = new VehicleRegistrySettings();
        settings.requiredAttributes = List.of("BRAND", "MODEL", "YEAR", "VIN", "OWNER_ID_HASH");
        settings.minConfidence = 0.85;
        settings.documentMaxAge = Duration.ofDays(30);
        settings.agreementMaxAge = Duration.ofDays(90);
        settings.agreementAutoApprove = true;
        settings.dailyQuota = 200;
        settings.quotaZone = "America/Costa_Rica";
        settings.registryRequestFee = Optional.empty();
        settings.registryRequestCurrency = "CRC";

        applier = new VehicleRegistryLookupApplier();
        applier.vehicleRepository = vehicleRepository;
        applier.registryLookupRepository = registryLookupRepository;
        applier.settings = settings;
        applier.vehicleEventProducer = vehicleEventProducer;
        applier.userIdentityPort = userIdentityPort;

        seller = UUID.randomUUID();
        vehicle = Vehicle.register("CR", "BCD123", seller);
        when(vehicleRepository.findByIdOptional(vehicle.getId())).thenReturn(Optional.of(vehicle));
        when(userIdentityPort.findIdentity(seller))
                .thenReturn(Optional.of(new UserIdentitySnapshot(seller, 2, SELLER_HASH, "CR")));
    }

    @Test
    void completeAnswerShouldVerifyWithoutAnalystOrDocument() {
        RegistryLookupResult result = apply(found(false), SELLER_HASH);

        assertEquals(RegistryLookupStatus.FOUND, result.status());
        assertTrue(result.anomalies().isEmpty());
        assertEquals(VehicleStatus.REGISTRY_VERIFIED, vehicle.getStatus());
        assertEquals(RegistrySource.AGREEMENT, vehicle.getRegistrySource());
        assertNull(vehicle.getRegistryDocumentMediaId());
        assertFalse(vehicle.isTransferBlocked());
        assertEquals(SELLER_HASH, vehicle.attributeValue(VehicleAttributeKey.OWNER_ID_HASH).orElseThrow());
        verify(vehicleEventProducer).registryCompleted(vehicle);
        verify(vehicleEventProducer, never()).registryAnomalyDetected(any(), any(), any());
        verify(registryLookupRepository).persist(any(RegistryLookup.class));
    }

    @Test
    void pledgeShouldBlockTheTransferAndBeReportedToTheSeller() {
        RegistryLookupResult result = apply(found(true), SELLER_HASH);

        assertEquals(VehicleStatus.REGISTRY_VERIFIED, vehicle.getStatus());
        assertTrue(vehicle.isTransferBlocked());
        assertTrue(vehicle.getEncumbrances().get(0).getDescription().contains("2500000"));
        assertEquals(List.of(RegistryAnomaly.PLEDGE), result.anomalies());
        verify(vehicleEventProducer).registryAnomalyDetected(vehicle, seller, List.of(RegistryAnomaly.PLEDGE));
    }

    @Test
    void anotherOwnerShouldBeReportedAndNotMatchTheSeller() {
        RegistryLookupResult result = apply(found(false), "someone-else-hash");

        assertEquals(VehicleStatus.REGISTRY_VERIFIED, vehicle.getStatus());
        assertEquals(List.of(RegistryAnomaly.OWNER_MISMATCH), result.anomalies());
        verify(vehicleEventProducer).registryAnomalyDetected(vehicle, seller, List.of(RegistryAnomaly.OWNER_MISMATCH));
    }

    @Test
    void autoApprovalOffShouldSendTheCaseToTheAnalyst() {
        settings.agreementAutoApprove = false;

        apply(found(false), SELLER_HASH);

        assertEquals(VehicleStatus.REGISTRY_IN_REVIEW, vehicle.getStatus());
        assertEquals(ReviewReason.AGREEMENT_REVIEW, vehicle.getReviewReason());
        verify(vehicleEventProducer).registryReviewRequired(vehicle, null);
        verify(vehicleEventProducer, never()).registryCompleted(any());
    }

    @Test
    void missingRequiredValueShouldSendTheCaseToTheAnalyst() {
        apply(found(false), null);

        assertEquals(VehicleStatus.REGISTRY_IN_REVIEW, vehicle.getStatus());
        assertEquals(ReviewReason.MISSING_REQUIRED, vehicle.getReviewReason());
        verify(vehicleEventProducer).registryReviewRequired(vehicle, null);
    }

    @Test
    void notFoundShouldLeaveTheVehiclePending() {
        RegistryLookupResult result = apply(VehicleRegistryRecord.notFound("REF-1"), null);

        assertEquals(RegistryLookupStatus.NOT_FOUND, result.status());
        assertEquals(VehicleStatus.REGISTRY_PENDING, vehicle.getStatus());
        assertTrue(vehicle.getAttributes().isEmpty());
        ArgumentCaptor<RegistryLookup> logged = ArgumentCaptor.forClass(RegistryLookup.class);
        verify(registryLookupRepository).persist(logged.capture());
        assertEquals(RegistryLookupOutcome.NOT_FOUND, logged.getValue().getOutcome());
        assertEquals("A", logged.getValue().getPlateClass());
    }

    @Test
    void failureShouldBeLoggedWithItsCode() {
        RegistryLookupResult result = applier.recordFailure(vehicle.getId(), "credid", "A",
                RegistryLookupPurpose.SELLER_LOOKUP, seller, "VEHICLE_REGISTRY_NOT_CONFIGURED");

        assertEquals(RegistryLookupStatus.NOT_CONFIGURED, result.status());
        assertEquals("VEHICLE_REGISTRY_NOT_CONFIGURED", result.errorCode());
        assertEquals(VehicleStatus.REGISTRY_PENDING, vehicle.getStatus());
        ArgumentCaptor<RegistryLookup> logged = ArgumentCaptor.forClass(RegistryLookup.class);
        verify(registryLookupRepository).persist(logged.capture());
        assertEquals(RegistryLookupOutcome.FAILED, logged.getValue().getOutcome());
    }

    @Test
    void pendingVehicleShouldBeQueried() {
        VehicleRegistryLookupApplier.Prepared prepared = prepare(false);

        assertTrue(prepared.callsProvider());
        assertEquals("BCD123", prepared.plate());
    }

    @Test
    void freshAgreementDataShouldBeReusedWithoutQuerying() {
        apply(found(false), SELLER_HASH);

        VehicleRegistryLookupApplier.Prepared prepared = prepare(false);

        assertFalse(prepared.callsProvider());
        assertEquals(RegistryLookupStatus.REUSED, prepared.result().status());
        verify(registryLookupRepository, never()).countAnsweredSince(anyString(), any(Instant.class));
    }

    @Test
    void transferRefreshShouldIgnoreTheReuse() {
        apply(found(false), SELLER_HASH);

        VehicleRegistryLookupApplier.Prepared prepared = applier.prepare(vehicle.getId(), null,
                RegistryLookupPurpose.TRANSFER_BEFORE, true, "credid");

        assertTrue(prepared.callsProvider());
    }

    @Test
    void exhaustedQuotaShouldNotQuery() {
        when(registryLookupRepository.countAnsweredSince(eq("credid"), any(Instant.class))).thenReturn(200L);

        VehicleRegistryLookupApplier.Prepared prepared = prepare(false);

        assertFalse(prepared.callsProvider());
        assertEquals(RegistryLookupStatus.QUOTA_EXHAUSTED, prepared.result().status());
        assertEquals("VEHICLE_REGISTRY_QUOTA_EXHAUSTED", prepared.result().errorCode());
    }

    @Test
    void vehicleWaitingForTheAnalystCannotBeQueried() {
        vehicle.attachRegistryDocument(UUID.randomUUID(), seller, Instant.now(), Duration.ofDays(30));

        assertThrows(InvalidStateException.class, () -> prepare(false));
    }

    private VehicleRegistryLookupApplier.Prepared prepare(boolean force) {
        return applier.prepare(vehicle.getId(), seller, RegistryLookupPurpose.SELLER_LOOKUP, force, "credid");
    }

    private RegistryLookupResult apply(VehicleRegistryRecord record, String ownerHash) {
        return applier.apply(vehicle.getId(), "credid", "A", RegistryLookupPurpose.SELLER_LOOKUP, seller, record, ownerHash);
    }

    private VehicleRegistryRecord found(boolean pledged) {
        Map<VehicleAttributeKey, String> attributes = new EnumMap<>(VehicleAttributeKey.class);
        attributes.put(VehicleAttributeKey.BRAND, "TOYOTA");
        attributes.put(VehicleAttributeKey.MODEL, "COROLLA");
        attributes.put(VehicleAttributeKey.YEAR, "2019");
        attributes.put(VehicleAttributeKey.VIN, "JTDBR32E720123456");
        return new VehicleRegistryRecord(true, attributes, pledged, pledged ? "2500000" : null,
                "PROPIETARIO DE PRUEBA", "100000001", "REF-1");
    }
}
