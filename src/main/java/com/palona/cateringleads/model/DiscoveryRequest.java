package com.palona.cateringleads.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DiscoveryRequest(
        Double latitude,
        Double longitude,
        @Min(500) @Max(20_000) Integer radiusMeters
) {
    public double resolvedLatitude() {
        return latitude == null ? 37.4914 : latitude;
    }

    public double resolvedLongitude() {
        return longitude == null ? -122.2280 : longitude;
    }

    public int resolvedRadiusMeters() {
        return radiusMeters == null ? 8_000 : radiusMeters;
    }
}
