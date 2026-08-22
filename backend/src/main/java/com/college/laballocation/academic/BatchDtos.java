package com.college.laballocation.academic;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class BatchDtos {
    private BatchDtos() {}

    public record CreateBatchRequest(@NotNull Long divisionId, @NotBlank String code, @Min(1) int strength) {}

    public record UpdateBatchRequest(@Min(1) int strength, boolean active) {}

    public record BatchResponse(Long id, Long divisionId, String divisionCode, String code, int strength, boolean active) {
        public static BatchResponse from(Batch batch) {
            return new BatchResponse(
                    batch.getId(),
                    batch.getDivision().getId(),
                    batch.getDivision().getCode(),
                    batch.getCode(),
                    batch.getStrength(),
                    batch.isActive());
        }
    }
}
