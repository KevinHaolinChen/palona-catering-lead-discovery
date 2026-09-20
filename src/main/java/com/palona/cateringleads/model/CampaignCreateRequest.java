package com.palona.cateringleads.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CampaignCreateRequest(
        @NotBlank String name,
        @NotBlank String businessType,
        @NotBlank String address,
        @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
        @Min(0) @Max(80_500) int radiusMeters
) {}
