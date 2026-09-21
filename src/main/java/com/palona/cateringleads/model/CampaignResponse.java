package com.palona.cateringleads.model;

import java.time.Instant;

public record CampaignResponse(
        String id,
        String name,
        String businessType,
        String address,
        double latitude,
        double longitude,
        int radiusMeters,
        boolean supportsCatering,
        String primaryDaypart,
        String priceTier,
        int deliveryRadiusMiles,
        boolean monitoringEnabled,
        int refreshIntervalDays,
        Instant lastRefreshAt,
        Instant createdAt
) {}
