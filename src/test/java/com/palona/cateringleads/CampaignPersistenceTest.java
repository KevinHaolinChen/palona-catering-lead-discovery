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
                "Harbor Pizza",
                "pizza",
                "123 Main St, Redwood City, CA",
                37.4914,
                -122.2280,
                8000
        ));

        assertThat(created.address()).isEqualTo("123 Main St, Redwood City, CA");
        assertThat(campaignService.findAll())
                .anySatisfy(campaign -> {
                    assertThat(campaign.id()).isEqualTo(created.id());
                    assertThat(campaign.name()).isEqualTo("Harbor Pizza");
                    assertThat(campaign.businessType()).isEqualTo("pizza");
                    assertThat(campaign.supportsCatering()).isTrue();
                    assertThat(campaign.primaryDaypart()).isEqualTo("lunch_dinner");
                    assertThat(campaign.priceTier()).isEqualTo("mid");
                    assertThat(campaign.deliveryRadiusMiles()).isEqualTo(5);
                });
    }
}
