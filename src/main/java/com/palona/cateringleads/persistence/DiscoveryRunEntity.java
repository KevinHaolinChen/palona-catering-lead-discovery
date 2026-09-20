package com.palona.cateringleads.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "discovery_runs")
public class DiscoveryRunEntity {
    @Id private String id;
    private String campaignId;
    private String status;
    private int candidateCount;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;

    protected DiscoveryRunEntity() {}
    public DiscoveryRunEntity(String id, String campaignId, String status, Instant createdAt) {
        this.id = id; this.campaignId = campaignId; this.status = status; this.createdAt = createdAt;
    }
    public void markRunning() { this.status = "DISCOVERING"; this.startedAt = Instant.now(); }
    public void markCompleted(int candidateCount) { this.status = "COMPLETED"; this.candidateCount = candidateCount; this.completedAt = Instant.now(); this.errorMessage = null; }
    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        String message = errorMessage == null ? "Unknown error" : errorMessage;
        this.errorMessage = message.substring(0, Math.min(1000, message.length()));
        this.completedAt = Instant.now();
    }
    public String getId() { return id; }
    public String getCampaignId() { return campaignId; }
    public String getStatus() { return status; }
    public int getCandidateCount() { return candidateCount; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
