package com.palona.cateringleads.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "campaigns")
public class CampaignEntity {
    @Id private String id;
    private String name;
    private String businessType;
    private String originAddress;
    private double latitude;
    private double longitude;
    private int radiusMeters;
    private boolean supportsCatering;
    private String primaryDaypart;
    private String priceTier;
    private int deliveryRadiusMiles;
    private boolean monitoringEnabled;
    private int refreshIntervalDays;
    private Instant lastRefreshAt;
    private Instant createdAt;

    protected CampaignEntity() {}

    public CampaignEntity(
            String id,
            String name,
            String businessType,
            String originAddress,
            double latitude,
            double longitude,
            int radiusMeters,
            boolean supportsCatering,
            String primaryDaypart,
            String priceTier,
            int deliveryRadiusMiles,
            boolean monitoringEnabled,
            int refreshIntervalDays,
            Instant lastRefreshAt,
            Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.businessType = businessType;
        this.originAddress = originAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
        this.supportsCatering = supportsCatering;
        this.primaryDaypart = primaryDaypart;
        this.priceTier = priceTier;
        this.deliveryRadiusMiles = deliveryRadiusMiles;
        this.monitoringEnabled = monitoringEnabled;
        this.refreshIntervalDays = refreshIntervalDays;
        this.lastRefreshAt = lastRefreshAt;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getBusinessType() { return businessType; }
    public String getOriginAddress() { return originAddress; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getRadiusMeters() { return radiusMeters; }
    public boolean isSupportsCatering() { return supportsCatering; }
    public String getPrimaryDaypart() { return primaryDaypart; }
    public String getPriceTier() { return priceTier; }
    public int getDeliveryRadiusMiles() { return deliveryRadiusMiles; }
    public boolean isMonitoringEnabled() { return monitoringEnabled; }
    public int getRefreshIntervalDays() { return refreshIntervalDays; }
    public Instant getLastRefreshAt() { return lastRefreshAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void configureMonitoring(boolean enabled, int intervalDays) {
        this.monitoringEnabled = enabled;
        this.refreshIntervalDays = Math.max(1, Math.min(30, intervalDays));
    }

    public void markRefreshed(Instant refreshedAt) {
        this.lastRefreshAt = refreshedAt;
    }
}
