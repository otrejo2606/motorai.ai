package ai.motoria.vehicle.dto;

import ai.motoria.vehicle.domain.EncumbranceType;

public record EncumbranceResponse(
        EncumbranceType type,
        String description,
        boolean blocksTransfer,
        Integer page) {
}
