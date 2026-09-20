package com.palona.cateringleads.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "campaign_profiles")
public class CampaignProfileEntity {
    @Id private String campaignId;
    private boolean supportsCatering;
    private String primaryDaypart;
    private String priceTier;
    private int deliveryRadiusMiles;

    protected CampaignProfileEntity() {}

    public CampaignProfileEntity(
            String campaignId,
            boolean supportsCatering,
            String primaryDaypart,
            String priceTier,
            int deliveryRadiusMiles
    ) {
        this.campaignId = campaignId;
        this.supportsCatering = supportsCatering;
        this.primaryDaypart = primaryDaypart;
        this.priceTier = priceTier;
        this.deliveryRadiusMiles = deliveryRadiusMiles;
    }

    public String getCampaignId() { return campaignId; }
    public boolean isSupportsCatering() { return supportsCatering; }
    public String getPrimaryDaypart() { return primaryDaypart; }
    public String getPriceTier() { return priceTier; }
    public int getDeliveryRadiusMiles() { return deliveryRadiusMiles; }
}
