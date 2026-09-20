package com.palona.cateringleads;

import com.palona.cateringleads.model.CampaignCreateRequest;
import com.palona.cateringleads.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CampaignPersistenceTest {
    @Autowired private CampaignService campaignService;

    @Test
    void campaignCanBePersistedAndReadBack() {
        var created = campaignService.create(new CampaignCreateRequest(
                "Peninsula Catering", "restaurant_catering", 37.4914, -122.2280, 8000));
        assertThat(campaignService.findAll())
                .anySatisfy(campaign -> assertThat(campaign.id()).isEqualTo(created.id()));
    }
}