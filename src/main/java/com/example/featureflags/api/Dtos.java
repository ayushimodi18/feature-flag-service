package com.example.featureflags.api;

import com.example.featureflags.domain.FeatureFlag;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;

public final class Dtos {
    private Dtos() {}

    public record CreateFlagRequest(
            @NotBlank
            @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{0,63}$",
                     message = "must be lowercase letters, digits, '-' or '_' (max 64 chars)")
            String name,
            @Size(max = 255) String description,
            @NotNull Boolean defaultEnabled) {}

    public record ToggleRequest(@NotNull Boolean enabled) {}

    public record FlagResponse(String name, String description, boolean enabled,
                               Instant createdAt, Instant updatedAt) {
        public static FlagResponse from(FeatureFlag f) {
            return new FlagResponse(f.getName(), f.getDescription(), f.isEnabled(),
                                    f.getCreatedAt(), f.getUpdatedAt());
        }
    }

    public record OverrideResponse(String flag, String userId, boolean enabled) {}

    public record EvaluationResponse(String flag, String userId, boolean enabled, String reason) {}

    public record UserFlagsResponse(String userId, Map<String, Boolean> flags) {}
}
