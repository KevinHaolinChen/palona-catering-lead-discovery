package com.palona.cateringleads.model;

public record DiscoveryCandidate(
        String name,
        String category,
        String address,
        String website,
        String phone,
        double latitude,
        double longitude,
        double distanceMiles,
        String sourceUrl,
        String source,
        int score,
        ScoreBreakdown scoreBreakdown,
        int profileFitScore,
        int evidenceScore,
        String evidenceSummary,
        String evidenceSourceUrl
) {
    public DiscoveryCandidate withEvidence(
            int finalScore,
            int profileFit,
            int evidence,
            String summary,
            String evidenceUrl
    ) {
        return new DiscoveryCandidate(
                name, category, address, website, phone, latitude, longitude, distanceMiles,
                sourceUrl, source, finalScore, scoreBreakdown, profileFit, evidence, summary, evidenceUrl
        );
    }
}
