package com.palona.cateringleads.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DiscoveryRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @Min(500) @Max(20_000) Integer radiusMeters
) {
    public double resolvedLatitude() { return latitude; }
    public double resolvedLongitude() { return longitude; }
    public int resolvedRadiusMeters() { return radiusMeters; }
}
