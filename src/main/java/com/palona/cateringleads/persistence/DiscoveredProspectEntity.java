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
    private String campaignId;
    private String prospectKey;
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
    private int learnedAdjustment;
    private int score;
    private String scoreExplanation;
    private int profileFitScore;
    private int evidenceScore;
    private String evidenceSummary;
    private String evidenceSourceUrl;
    private String pipelineStage;
    private Instant nextFollowUpAt;
    private Instant lastContactedAt;
    private String pipelineNote;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private Instant createdAt;

    protected DiscoveredProspectEntity() {}

    public static DiscoveredProspectEntity from(String runId, DiscoveryCandidate candidate) {
        return from(runId, null, null, candidate, null, 0);
    }

    public static DiscoveredProspectEntity from(
            String runId,
            String campaignId,
            String prospectKey,
            DiscoveryCandidate candidate,
            DiscoveredProspectEntity previous,
            int learnedAdjustment
    ) {
        Instant now = Instant.now();
        DiscoveredProspectEntity entity = new DiscoveredProspectEntity();
        entity.id = UUID.randomUUID().toString();
        entity.runId = runId;
        entity.campaignId = campaignId;
        entity.prospectKey = prospectKey;
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
        entity.learnedAdjustment = learnedAdjustment;
        entity.score = clampScore(candidate.score() + learnedAdjustment);
        entity.scoreExplanation = baseExplanation(candidate) + learningExplanation(learnedAdjustment);

        if (previous != null) {
            entity.pipelineStage = previous.getPipelineStage();
            entity.nextFollowUpAt = previous.getNextFollowUpAt();
            entity.lastContactedAt = previous.getLastContactedAt();
            entity.pipelineNote = previous.getPipelineNote();
            entity.firstSeenAt = previous.getFirstSeenAt() == null ? previous.getCreatedAt() : previous.getFirstSeenAt();
        } else {
            entity.pipelineStage = "NEW";
            entity.firstSeenAt = now;
        }

        entity.lastSeenAt = now;
        entity.createdAt = now;
        return entity;
    }

    public void applyEvidence(
            int adjustedScore,
            int profileFitScore,
            int evidenceScore,
            String evidenceSummary,
            String evidenceSourceUrl
    ) {
        this.score = clampScore(adjustedScore + learnedAdjustment);
        this.profileFitScore = profileFitScore;
        this.evidenceScore = evidenceScore;
        this.evidenceSummary = evidenceSummary;
        this.evidenceSourceUrl = evidenceSourceUrl;

        String base = this.scoreExplanation == null
                ? ""
                : this.scoreExplanation
                        .replaceAll(", profile=\\d+, evidence=\\d+$", "")
                        .replaceAll(", profile=\\d+, evidence=\\d+, learning=[+-]?\\d+$", "");
        this.scoreExplanation = base
                + ", profile=" + profileFitScore
                + ", evidence=" + evidenceScore
                + learningExplanation(learnedAdjustment);
    }

    public void updatePipeline(
            String stage,
            Instant nextFollowUpAt,
            String note,
            boolean markContacted
    ) {
        this.pipelineStage = stage;
        this.nextFollowUpAt = nextFollowUpAt;
        this.pipelineNote = note == null || note.isBlank()
                ? null
                : note.substring(0, Math.min(2000, note.length())).trim();
        if (markContacted) {
            this.lastContactedAt = Instant.now();
        }
    }

    private static String baseExplanation(DiscoveryCandidate candidate) {
        return "proximity=" + candidate.scoreBreakdown().proximity()
                + ", scale=" + candidate.scoreBreakdown().organizationScale()
                + ", events=" + candidate.scoreBreakdown().eventMeetingSignal()
                + ", need=" + candidate.scoreBreakdown().foodNeedSignal()
                + ", contact=" + candidate.scoreBreakdown().contactability();
    }

    private static String learningExplanation(int adjustment) {
        return adjustment == 0 ? "" : ", learning=" + (adjustment > 0 ? "+" : "") + adjustment;
    }

    private static int clampScore(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public String getId() { return id; }
    public String getRunId() { return runId; }
    public String getCampaignId() { return campaignId; }
    public String getProspectKey() { return prospectKey; }
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
    public int getLearnedAdjustment() { return learnedAdjustment; }
    public int getScore() { return score; }
    public String getScoreExplanation() { return scoreExplanation; }
    public int getProfileFitScore() { return profileFitScore; }
    public int getEvidenceScore() { return evidenceScore; }
    public String getEvidenceSummary() { return evidenceSummary; }
    public String getEvidenceSourceUrl() { return evidenceSourceUrl; }
    public String getPipelineStage() { return pipelineStage == null ? "NEW" : pipelineStage; }
    public Instant getNextFollowUpAt() { return nextFollowUpAt; }
    public Instant getLastContactedAt() { return lastContactedAt; }
    public String getPipelineNote() { return pipelineNote; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }
}
