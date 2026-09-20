package com.palona.cateringleads.service;

import com.palona.cateringleads.model.ScoreBreakdown;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ScoringService {

    private static final Map<String, int[]> CATEGORY_SIGNALS = Map.ofEntries(
            Map.entry("community_event_space", new int[]{20, 25, 19}),
            Map.entry("law_office", new int[]{17, 23, 18}),
            Map.entry("university_campus", new int[]{20, 23, 17}),
            Map.entry("corporate_hq", new int[]{18, 19, 17}),
            Map.entry("corporate_office", new int[]{15, 17, 16}),
            Map.entry("hospital", new int[]{20, 14, 16}),
            Map.entry("corporate_campus", new int[]{20, 18, 17})
    );

    public ScoreBreakdown heuristicScore(
            String category,
            double distanceMiles,
            boolean hasWebsite,
            boolean hasPhone
    ) {
        int[] signals = CATEGORY_SIGNALS.getOrDefault(category, new int[]{10, 10, 10});
        return new ScoreBreakdown(
                proximityScore(distanceMiles),
                signals[0],
                signals[1],
                signals[2],
                contactabilityScore(hasWebsite, hasPhone)
        );
    }

    public ScoreBreakdown heuristicScore(String category) {
        return heuristicScore(category, 8.0, false, false);
    }

    int proximityScore(double distanceMiles) {
        if (distanceMiles <= 2.0) return 25;
        if (distanceMiles <= 5.0) return 22;
        if (distanceMiles <= 10.0) return 18;
        if (distanceMiles <= 15.0) return 12;
        return 6;
    }

    int contactabilityScore(boolean hasWebsite, boolean hasPhone) {
        if (hasWebsite && hasPhone) return 10;
        if (hasWebsite || hasPhone) return 7;
        return 2;
    }
}
