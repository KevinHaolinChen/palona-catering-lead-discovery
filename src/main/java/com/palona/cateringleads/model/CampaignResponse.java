package com.palona.cateringleads.model;
import java.time.Instant;
public record CampaignResponse(
        String id,
        String name,
        String businessType,
        double latitude,
        double longitude,
        int radiusMeters,
        Instant createdAt
) {}