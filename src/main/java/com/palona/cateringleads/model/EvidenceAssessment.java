package com.palona.cateringleads.model;

public record EvidenceAssessment(
        int profileFitScore,
        int evidenceScore,
        int finalScore,
        String summary,
        String sourceUrl
) {}
