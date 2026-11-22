package ai.motoria.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitIdentityVerificationRequest(
        @NotNull UUID documentFrontMediaId,
        @NotNull UUID documentBackMediaId,
        @NotNull UUID selfieMediaId) {
}
