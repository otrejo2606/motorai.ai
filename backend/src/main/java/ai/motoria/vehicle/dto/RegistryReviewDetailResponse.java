package ai.motoria.vehicle.dto;

import ai.motoria.vehicle.domain.RegistrySource;
import ai.motoria.vehicle.domain.ReviewReason;
import ai.motoria.vehicle.domain.VehicleStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param registrySource AGREEMENT when the data came from the registry agreement (no document nor pages);
 *                       DOCUMENT or null (vehicles before V9) when it came from the registry PDF
 */
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
        List<EncumbranceResponse> encumbrances,
        RegistrySource registrySource) {
}
