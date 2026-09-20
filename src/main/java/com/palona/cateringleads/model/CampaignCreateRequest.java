package com.palona.cateringleads.model;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
public record CampaignCreateRequest(
        @NotBlank String name,
        @NotBlank String businessType,
        double latitude,
        double longitude,
        @Min(500) @Max(20_000) int radiusMeters
) {}