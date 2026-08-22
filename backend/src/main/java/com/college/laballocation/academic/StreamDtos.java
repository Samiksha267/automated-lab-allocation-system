package com.college.laballocation.academic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class StreamDtos {
    private StreamDtos() {}

    public record CreateStreamRequest(@NotNull Long programId, @NotBlank String code, @NotBlank String name) {}

    public record UpdateStreamRequest(@NotBlank String name, boolean active) {}

    public record StreamResponse(Long id, Long programId, String programCode, String code, String name, boolean active) {
        public static StreamResponse from(Stream stream) {
            return new StreamResponse(
                    stream.getId(),
                    stream.getProgram().getId(),
                    stream.getProgram().getCode(),
                    stream.getCode(),
                    stream.getName(),
                    stream.isActive());
        }
    }
}
