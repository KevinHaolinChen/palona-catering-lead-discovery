package com.palona.cateringleads.service;

import com.palona.cateringleads.model.CampaignCreateRequest;
import com.palona.cateringleads.model.CampaignResponse;
import com.palona.cateringleads.model.DiscoveryRunResponse;
import com.palona.cateringleads.persistence.CampaignEntity;
import com.palona.cateringleads.persistence.CampaignRepository;
import com.palona.cateringleads.persistence.DiscoveryRunEntity;
import com.palona.cateringleads.persistence.DiscoveryRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CampaignService {
    private final CampaignRepository campaignRepository;
    private final DiscoveryRunRepository runRepository;
    private final DiscoveryRunProcessor runProcessor;

    public CampaignService(
            CampaignRepository campaignRepository,
            DiscoveryRunRepository runRepository,
            DiscoveryRunProcessor runProcessor
    ) {
        this.campaignRepository = campaignRepository;
        this.runRepository = runRepository;
        this.runProcessor = runProcessor;
    }

    @Transactional
    public CampaignResponse create(CampaignCreateRequest request) {
        String businessType = request.businessType().trim();
        boolean supportsCatering = request.supportsCatering() == null || request.supportsCatering();
        String primaryDaypart = valueOrDefault(request.primaryDaypart(), inferDaypart(businessType));
        String priceTier = valueOrDefault(request.priceTier(), "mid");
        int deliveryRadiusMiles = request.deliveryRadiusMiles() == null
                ? defaultDeliveryRadius(request.radiusMeters())
                : request.deliveryRadiusMiles();

        CampaignEntity entity = new CampaignEntity(
                UUID.randomUUID().toString(),
                request.name().trim(),
                businessType,
                request.address().trim(),
                request.latitude(),
                request.longitude(),
                request.radiusMeters(),
                supportsCatering,
                primaryDaypart,
                priceTier,
                deliveryRadiusMiles,
                false,
                7,
                null,
                Instant.now()
        );
        return toResponse(campaignRepository.save(entity));
    }

    public List<CampaignResponse> findAll() {
        return campaignRepository.findAll().stream().map(CampaignService::toResponse).toList();
    }

    public DiscoveryRunResponse startRun(String campaignId) {
        CampaignEntity campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
        Instant now = Instant.now();
        DiscoveryRunEntity run = runRepository.save(new DiscoveryRunEntity(
                UUID.randomUUID().toString(), campaign.getId(), "QUEUED", now));
        runProcessor.processAsync(run.getId(), campaign.getId());
        return toResponse(run);
    }

    @Transactional
    public CampaignResponse configureMonitoring(String campaignId, boolean enabled, int intervalDays) {
        CampaignEntity campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));
        campaign.configureMonitoring(enabled, intervalDays);
        return toResponse(campaignRepository.save(campaign));
    }

    public DiscoveryRunResponse findRun(String runId) {
        return runRepository.findById(runId).map(CampaignService::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Discovery run not found: " + runId));
    }

    private static CampaignResponse toResponse(CampaignEntity c) {
        return new CampaignResponse(
                c.getId(),
                c.getName(),
                c.getBusinessType(),
                c.getOriginAddress(),
                c.getLatitude(),
                c.getLongitude(),
                c.getRadiusMeters(),
                c.isSupportsCatering(),
                c.getPrimaryDaypart(),
                c.getPriceTier(),
                c.getDeliveryRadiusMiles(),
                c.isMonitoringEnabled(),
                c.getRefreshIntervalDays(),
                c.getLastRefreshAt(),
                c.getCreatedAt()
        );
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String inferDaypart(String businessType) {
        return switch (businessType.toLowerCase(Locale.ROOT)) {
            case "breakfast_brunch", "cafe_bakery" -> "breakfast_lunch";
            case "pizza", "fast_casual" -> "lunch_dinner";
            default -> "all_day";
        };
    }

    private static int defaultDeliveryRadius(int searchRadiusMeters) {
        int searchMiles = (int) Math.round(searchRadiusMeters / 1609.344);
        return Math.max(1, Math.min(10, searchMiles));
    }

    static DiscoveryRunResponse toResponse(DiscoveryRunEntity r) {
        return new DiscoveryRunResponse(
                r.getId(),
                r.getCampaignId(),
                r.getStatus(),
                r.getCandidateCount(),
                r.getErrorMessage(),
                r.getStartedAt(),
                r.getCompletedAt(),
                r.getCreatedAt()
        );
    }
}
