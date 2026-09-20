package com.palona.cateringleads.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DiscoveryRunRepository extends JpaRepository<DiscoveryRunEntity, String> {
    List<DiscoveryRunEntity> findByCampaignIdOrderByCreatedAtDesc(String campaignId);
}