package com.palona.cateringleads.model;

public record ProspectEvidenceResponse(
        String prospectId,
        int baseScore,
        int profileFitScore,
        int evidenceScore,
        int adjustedScore,
        String evidenceSummary,
        String evidenceSourceUrl
) {}
