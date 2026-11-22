package ai.motoria.vehicle.dto;

import ai.motoria.vehicle.domain.ReviewReason;
import ai.motoria.vehicle.domain.VehicleStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegistryReviewDetailResponse(
        UUID vehicleId,
        String countryCode,
        String plate,
        VehicleStatus status,
        ReviewReason reviewReason,
        String rejectionReason,
        UUID documentMediaId,
        LocalDate documentDate,
        UUID inferenceId,
        List<ReviewPageResponse> pages,
        List<ReviewAttributeResponse> attributes,
        List<EncumbranceResponse> encumbrances) {
}
