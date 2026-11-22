package ai.motoria.vehicle.mapper;

import ai.motoria.common.port.OwnershipMatch;
import ai.motoria.common.port.VehicleAttributeSnapshot;
import ai.motoria.common.port.VehicleSnapshot;
import ai.motoria.vehicle.domain.RegistryEncumbrance;
import ai.motoria.vehicle.domain.RegistryRequest;
import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.domain.VehicleAttribute;
import ai.motoria.vehicle.domain.VehicleStatus;
import ai.motoria.vehicle.dto.EncumbranceResponse;
import ai.motoria.vehicle.dto.FeeResponse;
import ai.motoria.vehicle.dto.RegistryRequestResponse;
import ai.motoria.vehicle.dto.RegistryReviewDetailResponse;
import ai.motoria.vehicle.dto.RegistryReviewSummaryResponse;
import ai.motoria.vehicle.dto.ReviewAttributeResponse;
import ai.motoria.vehicle.dto.ReviewPageResponse;
import ai.motoria.vehicle.dto.VehicleAttributeResponse;
import ai.motoria.vehicle.dto.VehicleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface VehicleMapper {

    @Mapping(target = "page", source = "evidencePage")
    EncumbranceResponse toResponse(RegistryEncumbrance encumbrance);

    @Mapping(target = "page", source = "evidencePage")
    ReviewAttributeResponse toReviewAttribute(VehicleAttribute attribute);

    default VehicleResponse toResponse(Vehicle vehicle, FeeResponse registryRequestFee, boolean registryRequestOpen,
                                       OwnershipMatch ownerMatch) {
        boolean verified = vehicle.getStatus() == VehicleStatus.REGISTRY_VERIFIED;
        List<VehicleAttributeResponse> attributes = vehicle.getAttributes().stream()
                .filter(attribute -> attribute.getKey().isPublic())
                .map(attribute -> new VehicleAttributeResponse(attribute.getKey(), attribute.getValue(),
                        attribute.getSource(), verified && attribute.getSource().isOfficial()))
                .toList();
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getCountryCode(),
                vehicle.getPlate(),
                vehicle.getStatus(),
                vehicle.getReviewReason(),
                vehicle.getRejectionReason(),
                vehicle.isTransferBlocked(),
                vehicle.getTransferBlockReason(),
                vehicle.getRegistryCheckedAt(),
                attributes,
                vehicle.getEncumbrances().stream().map(this::toResponse).toList(),
                registryRequestFee,
                registryRequestOpen,
                ownerMatch);
    }

    default RegistryReviewSummaryResponse toSummary(Vehicle vehicle) {
        return new RegistryReviewSummaryResponse(
                vehicle.getId(),
                vehicle.getCountryCode(),
                vehicle.getPlate(),
                vehicle.getStatus(),
                vehicle.getReviewReason(),
                vehicle.getRegistryDocumentMediaId(),
                vehicle.getUpdatedAt());
    }

    default RegistryReviewDetailResponse toReviewDetail(Vehicle vehicle, List<ReviewPageResponse> pages) {
        return new RegistryReviewDetailResponse(
                vehicle.getId(),
                vehicle.getCountryCode(),
                vehicle.getPlate(),
                vehicle.getStatus(),
                vehicle.getReviewReason(),
                vehicle.getRejectionReason(),
                vehicle.getRegistryDocumentMediaId(),
                vehicle.getRegistryDocumentDate(),
                vehicle.getExtractionInferenceId(),
                pages,
                vehicle.getAttributes().stream().map(this::toReviewAttribute).toList(),
                vehicle.getEncumbrances().stream().map(this::toResponse).toList());
    }

    default RegistryRequestResponse toResponse(RegistryRequest request, Vehicle vehicle) {
        return new RegistryRequestResponse(
                request.getId(),
                request.getVehicleId(),
                vehicle != null ? vehicle.getCountryCode() : null,
                vehicle != null ? vehicle.getPlate() : null,
                request.getStatus(),
                new FeeResponse(request.getAcceptedFee(), request.getCurrency()),
                request.getRequestedBy(),
                request.getDocumentMediaId(),
                request.getFulfilledAt(),
                request.getCreatedAt());
    }

    /**
     * Snapshot for other modules: owner data excluded.
     */
    default VehicleSnapshot toSnapshot(Vehicle vehicle) {
        boolean verified = vehicle.getStatus() == VehicleStatus.REGISTRY_VERIFIED;
        List<VehicleAttributeSnapshot> attributes = vehicle.getAttributes().stream()
                .filter(attribute -> !attribute.getKey().isOwnerData())
                .map(attribute -> new VehicleAttributeSnapshot(attribute.getKey().name(), attribute.getValue(),
                        attribute.getSource().name(), verified && attribute.getSource().isOfficial()))
                .toList();
        return new VehicleSnapshot(
                vehicle.getId(),
                vehicle.getCountryCode(),
                vehicle.getPlate(),
                vehicle.getStatus().name(),
                vehicle.isTransferBlocked(),
                vehicle.getTransferBlockReason(),
                vehicle.getRegistryCheckedAt(),
                attributes);
    }
}
