package com.palona.cateringleads.model;

public record DiscoveryCandidate(
        String name,
        String category,
        String address,
        String website,
        String phone,
        double distanceMiles,
        String sourceUrl,
        String source,
        int score,
        ScoreBreakdown scoreBreakdown
) {}
