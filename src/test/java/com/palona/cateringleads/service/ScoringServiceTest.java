package com.palona.cateringleads.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {

    private final ScoringService scoringService = new ScoringService();

    @Test
    void scoreNeverExceedsOneHundred() {
        int score = scoringService.heuristicScore("community_event_space").total();
        assertThat(score).isBetween(0, 100);
    }

    @Test
    void eventSpaceScoresAboveGenericOrganization() {
        int eventSpace = scoringService.heuristicScore("community_event_space").total();
        int generic = scoringService.heuristicScore("organization").total();
        assertThat(eventSpace).isGreaterThan(generic);
    }
}
