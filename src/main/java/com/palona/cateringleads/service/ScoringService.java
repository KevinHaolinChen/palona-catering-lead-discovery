package com.palona.cateringleads.service;

import com.palona.cateringleads.model.ScoreBreakdown;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ScoringService {

    private static final Map<String, int[]> CATEGORY_WEIGHTS = Map.ofEntries(
            Map.entry("community_event_space", new int[]{20, 20, 25, 19, 10}),
            Map.entry("law_office", new int[]{23, 17, 23, 18, 9}),
            Map.entry("university_campus", new int[]{16, 20, 23, 17, 8}),
            Map.entry("corporate_hq", new int[]{20, 18, 19, 17, 8}),
            Map.entry("corporate_office", new int[]{22, 15, 17, 16, 8}),
            Map.entry("hospital", new int[]{24, 20, 14, 16, 6}),
            Map.entry("corporate_campus", new int[]{12, 20, 18, 17, 6})
    );

    /**
     * Deterministic cold-start score for newly discovered candidates.
     * Cached demo leads contain hand-reviewed evidence-conditioned adjustments.
     */
    public ScoreBreakdown heuristicScore(String category) {
        int[] values = CATEGORY_WEIGHTS.getOrDefault(category, new int[]{15, 10, 10, 10, 5});
        return new ScoreBreakdown(values[0], values[1], values[2], values[3], values[4]);
    }
}
