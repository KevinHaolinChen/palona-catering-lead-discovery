package com.palona.cateringleads.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscoveredProspectRepository extends JpaRepository<DiscoveredProspectEntity, String> {
    List<DiscoveredProspectEntity> findByRunIdOrderByScoreDesc(String runId);
    List<DiscoveredProspectEntity> findByCampaignIdOrderByCreatedAtDesc(String campaignId);
    List<DiscoveredProspectEntity> findByCampaignIdAndProspectKeyOrderByCreatedAtDesc(
            String campaignId,
            String prospectKey
    );
}
