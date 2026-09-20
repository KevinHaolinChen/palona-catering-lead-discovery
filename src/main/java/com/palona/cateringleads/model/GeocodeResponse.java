package com.palona.cateringleads.model;

public record GeocodeResponse(
        String query,
        String displayName,
        double latitude,
        double longitude,
        String provider
) {}
