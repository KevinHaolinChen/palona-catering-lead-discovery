package com.palona.cateringleads;

import com.palona.cateringleads.model.CampaignCreateRequest;
import com.palona.cateringleads.model.DiscoveryCandidate;
import com.palona.cateringleads.model.ProspectPipelineUpdateRequest;
import com.palona.cateringleads.model.ScoreBreakdown;
import com.palona.cateringleads.persistence.DiscoveredProspectEntity;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import com.palona.cateringleads.persistence.DiscoveryRunEntity;
import com.palona.cateringleads.persistence.DiscoveryRunRepository;
import com.palona.cateringleads.service.CampaignService;
import com.palona.cateringleads.service.OutcomeLearningService;
import com.palona.cateringleads.service.PipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SalesLoopTest {

    @Autowired private CampaignService campaignService;
    @Autowired private DiscoveryRunRepository runRepository;
    @Autowired private DiscoveredProspectRepository prospectRepository;
    @Autowired private PipelineService pipelineService;
    @Autowired private OutcomeLearningService outcomeLearningService;

    @Test
    void pipelineOutcomeSurvivesRefreshAndInfluencesFutureRanking() {
        var campaign = campaignService.create(new CampaignCreateRequest(
                "Gather Test Pizza",
                "pizza",
                "1 Market St, San Francisco, CA",
                37.7936,
                -122.3958,
                8047
        ));

        String runOneId = UUID.randomUUID().toString();
        runRepository.save(new DiscoveryRunEntity(
                runOneId,
                campaign.id(),
                "COMPLETED",
                Instant.now()
        ));

        DiscoveryCandidate candidate = new DiscoveryCandidate(
                "Alpha Office",
                "corporate_office",
                "100 Main St, San Francisco, CA",
                "https://alpha.example.org",
                null,
                37.7900,
                -122.4000,
                1.2,
                "https://www.openstreetmap.org/node/1",
                "test",
                70,
                new ScoreBreakdown(25, 15, 17, 16, 7)
        );

        String key = "domain:alpha.example.org";
        DiscoveredProspectEntity first = DiscoveredProspectEntity.from(
                runOneId,
                campaign.id(),
                key,
                candidate,
                null,
                0
        );
        prospectRepository.save(first);

        DiscoveredProspectEntity won = pipelineService.update(
                first.getId(),
                new ProspectPipelineUpdateRequest("WON", null, "Converted to a catering order")
        );

        int learned = outcomeLearningService.adjustmentFor(
                campaign.id(),
                "corporate_office"
        );
        assertThat(learned).isPositive();

        String runTwoId = UUID.randomUUID().toString();
        runRepository.save(new DiscoveryRunEntity(
                runTwoId,
                campaign.id(),
                "COMPLETED",
                Instant.now()
        ));

        DiscoveredProspectEntity refreshed = DiscoveredProspectEntity.from(
                runTwoId,
                campaign.id(),
                key,
                candidate,
                won,
                learned
        );
        prospectRepository.save(refreshed);

        assertThat(refreshed.getPipelineStage()).isEqualTo("WON");
        assertThat(refreshed.getLearnedAdjustment()).isEqualTo(learned);
        assertThat(refreshed.getScore()).isEqualTo(70 + learned);
        assertThat(refreshed.getFirstSeenAt()).isEqualTo(won.getFirstSeenAt());

        var dashboard = pipelineService.dashboard(campaign.id());
        assertThat(dashboard.totalProspects()).isEqualTo(1);
        assertThat(dashboard.wonCount()).isEqualTo(1);
        assertThat(dashboard.learnedCategoryAdjustments())
                .containsEntry("corporate_office", learned);
    }
}
