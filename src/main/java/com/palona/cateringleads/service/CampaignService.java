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
import java.util.UUID;

@Service
public class CampaignService {
    private final CampaignRepository campaignRepository;
    private final DiscoveryRunRepository runRepository;
    private final DiscoveryRunProcessor runProcessor;

    public CampaignService(CampaignRepository campaignRepository, DiscoveryRunRepository runRepository, DiscoveryRunProcessor runProcessor) {
        this.campaignRepository = campaignRepository;
        this.runRepository = runRepository;
        this.runProcessor = runProcessor;
    }

    @Transactional
    public CampaignResponse create(CampaignCreateRequest request) {
        CampaignEntity entity = new CampaignEntity(
                UUID.randomUUID().toString(),
                request.name().trim(),
                request.businessType().trim(),
                request.address().trim(),
                request.latitude(),
                request.longitude(),
                request.radiusMeters(),
                request.supportsCatering() == null || request.supportsCatering(),
                valueOrDefault(request.primaryDaypart(), inferDaypart(request.businessType())),
                valueOrDefault(request.priceTier(), "mid"),
                request.deliveryRadiusMiles() == null
                        ? Math.max(1, Math.min(10, (int) Math.round(request.radiusMeters() / 1609.344)))
                        : request.deliveryRadiusMiles(),
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
        DiscoveryRunEntity run = runRepository.save(new DiscoveryRunEntity(
                UUID.randomUUID().toString(), campaign.getId(), "QUEUED", Instant.now()));
        runProcessor.processAsync(run.getId(), campaign.getId());
        return toResponse(run);
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
                c.getCreatedAt()
        );
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String inferDaypart(String businessType) {
        String type = businessType == null ? "" : businessType.trim().toLowerCase();
        return switch (type) {
            case "breakfast_brunch", "cafe_bakery" -> "breakfast_lunch";
            case "pizza", "fast_casual" -> "lunch_dinner";
            default -> "all_day";
        };
    }

    static DiscoveryRunResponse toResponse(DiscoveryRunEntity r) {
        return new DiscoveryRunResponse(r.getId(), r.getCampaignId(), r.getStatus(), r.getCandidateCount(),
                r.getErrorMessage(), r.getStartedAt(), r.getCompletedAt(), r.getCreatedAt());
    }
}
