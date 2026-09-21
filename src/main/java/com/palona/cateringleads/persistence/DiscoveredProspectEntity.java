package com.palona.cateringleads.persistence;

import com.palona.cateringleads.model.DiscoveryCandidate;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discovered_prospects")
public class DiscoveredProspectEntity {
    @Id private String id;
    private String runId;
    private String organization;
    private String category;
    private String address;
    private String website;
    private String phone;
    private double latitude;
    private double longitude;
    private double distanceMiles;
    private String sourceUrl;
    private String sourceName;
    private int baseScore;
    private int score;
    private String scoreExplanation;
    private int profileFitScore;
    private int evidenceScore;
    private String evidenceSummary;
    private String evidenceSourceUrl;
    private Instant createdAt;

    protected DiscoveredProspectEntity() {}

    public static DiscoveredProspectEntity from(String runId, DiscoveryCandidate candidate) {
        DiscoveredProspectEntity entity = new DiscoveredProspectEntity();
        entity.id = UUID.randomUUID().toString();
        entity.runId = runId;
        entity.organization = candidate.name();
        entity.category = candidate.category();
        entity.address = candidate.address();
        entity.website = candidate.website();
        entity.phone = candidate.phone();
        entity.latitude = candidate.latitude();
        entity.longitude = candidate.longitude();
        entity.distanceMiles = candidate.distanceMiles();
        entity.sourceUrl = candidate.sourceUrl();
        entity.sourceName = candidate.source();
        entity.baseScore = candidate.score();
        entity.score = candidate.score();
        entity.scoreExplanation = "proximity=" + candidate.scoreBreakdown().proximity()
                + ", scale=" + candidate.scoreBreakdown().organizationScale()
                + ", events=" + candidate.scoreBreakdown().eventMeetingSignal()
                + ", need=" + candidate.scoreBreakdown().foodNeedSignal()
                + ", contact=" + candidate.scoreBreakdown().contactability();
        entity.createdAt = Instant.now();
        return entity;
    }

    public void applyEvidence(
            int adjustedScore,
            int profileFitScore,
            int evidenceScore,
            String evidenceSummary,
            String evidenceSourceUrl
    ) {
        this.score = adjustedScore;
        this.profileFitScore = profileFitScore;
        this.evidenceScore = evidenceScore;
        this.evidenceSummary = evidenceSummary;
        this.evidenceSourceUrl = evidenceSourceUrl;

        String base = this.scoreExplanation == null ? "" : this.scoreExplanation.replaceAll(", profile=\\d+, evidence=\\d+$", "");
        this.scoreExplanation = base + ", profile=" + profileFitScore + ", evidence=" + evidenceScore;
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getOrganization() { return organization; }
    public String getCategory() { return category; }
    public String getAddress() { return address; }
    public String getWebsite() { return website; }
    public String getPhone() { return phone; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getDistanceMiles() { return distanceMiles; }
    public String getSourceUrl() { return sourceUrl; }
    public String getSourceName() { return sourceName; }
    public int getBaseScore() { return baseScore; }
    public int getScore() { return score; }
    public String getScoreExplanation() { return scoreExplanation; }
    public int getProfileFitScore() { return profileFitScore; }
    public int getEvidenceScore() { return evidenceScore; }
    public String getEvidenceSummary() { return evidenceSummary; }
    public String getEvidenceSourceUrl() { return evidenceSourceUrl; }
    public Instant getCreatedAt() { return createdAt; }
}
