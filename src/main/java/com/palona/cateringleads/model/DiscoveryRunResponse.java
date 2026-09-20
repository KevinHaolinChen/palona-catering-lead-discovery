package com.palona.cateringleads.model;
import java.time.Instant;
public record DiscoveryRunResponse(
        String id,
        String campaignId,
        String status,
        int candidateCount,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {}