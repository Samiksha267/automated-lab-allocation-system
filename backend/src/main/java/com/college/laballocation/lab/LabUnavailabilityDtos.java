package com.college.laballocation.lab;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class LabUnavailabilityDtos {
    private LabUnavailabilityDtos() {}

    public record CreateLabUnavailabilityRequest(
            @NotNull Instant startDateTime, @NotNull Instant endDateTime, @NotBlank String reason) {}

    public record LabUnavailabilityResponse(
            Long id, Long labId, String labCode, Instant startDateTime, Instant endDateTime, String reason, String createdByEmail) {
        static LabUnavailabilityResponse from(LabUnavailability unavailability) {
            return new LabUnavailabilityResponse(
                    unavailability.getId(),
                    unavailability.getLab().getId(),
                    unavailability.getLab().getCode(),
                    unavailability.getStartDateTime(),
                    unavailability.getEndDateTime(),
                    unavailability.getReason(),
                    unavailability.getCreatedBy().getEmail());
        }
    }
}
