package com.palona.cateringleads.service;

import com.palona.cateringleads.persistence.CampaignEntity;
import com.palona.cateringleads.persistence.CampaignRepository;
import com.palona.cateringleads.persistence.DiscoveryRunEntity;
import com.palona.cateringleads.persistence.DiscoveryRunRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class RadiusWatchService {

    private final CampaignRepository campaignRepository;
    private final DiscoveryRunRepository runRepository;
    private final CampaignService campaignService;

    public RadiusWatchService(
            CampaignRepository campaignRepository,
            DiscoveryRunRepository runRepository,
            CampaignService campaignService
    ) {
        this.campaignRepository = campaignRepository;
        this.runRepository = runRepository;
        this.campaignService = campaignService;
    }

    @Scheduled(fixedDelayString = "${gather.radius-watch.poll-ms:3600000}")
    public void refreshDueCampaigns() {
        Instant now = Instant.now();

        for (CampaignEntity campaign : campaignRepository.findAll()) {
            if (!campaign.isMonitoringEnabled()) continue;
            if (!isDue(campaign, now)) continue;
            if (hasActiveRun(campaign.getId())) continue;

            try {
                campaignService.startRun(campaign.getId());
            } catch (Exception ignored) {
                // A future watch cycle can retry. Interactive runs surface errors directly.
            }
        }
    }

    private boolean isDue(CampaignEntity campaign, Instant now) {
        Instant lastRefresh = campaign.getLastRefreshAt();
        if (lastRefresh == null) return true;

        int intervalDays = Math.max(1, campaign.getRefreshIntervalDays());
        return !lastRefresh.plus(Duration.ofDays(intervalDays)).isAfter(now);
    }

    private boolean hasActiveRun(String campaignId) {
        List<DiscoveryRunEntity> runs = runRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);
        if (runs.isEmpty()) return false;

        String status = runs.get(0).getStatus();
        return Set.of("QUEUED", "DISCOVERING").contains(status);
    }
}
