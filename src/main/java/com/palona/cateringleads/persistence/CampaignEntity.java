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
            Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.businessType = businessType;
        this.originAddress = originAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getBusinessType() { return businessType; }
    public String getOriginAddress() { return originAddress; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getRadiusMeters() { return radiusMeters; }
    public Instant getCreatedAt() { return createdAt; }
}
