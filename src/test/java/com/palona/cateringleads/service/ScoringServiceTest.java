package com.palona.cateringleads.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {
    private final ScoringService scoringService = new ScoringService();

    @Test
    void scoreNeverExceedsOneHundred() {
        int score = scoringService.heuristicScore("community_event_space", 1.0, true, true).total();
        assertThat(score).isBetween(0, 100);
    }

    @Test
    void eventSpaceScoresAboveGenericOrganizationAtSameDistance() {
        int eventSpace = scoringService.heuristicScore("community_event_space", 4.0, true, false).total();
        int generic = scoringService.heuristicScore("organization", 4.0, true, false).total();
        assertThat(eventSpace).isGreaterThan(generic);
    }

    @Test
    void nearerCandidateGetsHigherProximityScore() {
        int near = scoringService.heuristicScore("corporate_office", 1.5, true, true).proximity();
        int far = scoringService.heuristicScore("corporate_office", 18.0, true, true).proximity();
        assertThat(near).isGreaterThan(far);
    }

    @Test
    void betterContactabilityRaisesScore() {
        int complete = scoringService.heuristicScore("corporate_office", 5.0, true, true).contactability();
        int absent = scoringService.heuristicScore("corporate_office", 5.0, false, false).contactability();
        assertThat(complete).isGreaterThan(absent);
    }
}