package com.palona.cateringleads.service;

import com.palona.cateringleads.model.CampaignDashboardResponse;
import com.palona.cateringleads.model.ProspectPipelineUpdateRequest;
import com.palona.cateringleads.persistence.DiscoveredProspectEntity;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PipelineService {

    private static final Set<String> STAGES = Set.of(
            "NEW", "REVIEWED", "CONTACTED", "FOLLOW_UP", "REPLIED", "WON", "LOST"
    );

    private final DiscoveredProspectRepository prospectRepository;
    private final OutcomeLearningService outcomeLearningService;

    public PipelineService(
            DiscoveredProspectRepository prospectRepository,
            OutcomeLearningService outcomeLearningService
    ) {
        this.prospectRepository = prospectRepository;
        this.outcomeLearningService = outcomeLearningService;
    }

    @Transactional
    public DiscoveredProspectEntity update(String prospectId, ProspectPipelineUpdateRequest request) {
        DiscoveredProspectEntity prospect = prospectRepository.findById(prospectId)
                .orElseThrow(() -> new IllegalArgumentException("Prospect not found: " + prospectId));

        String stage = normalizeStage(request.stage());
        boolean markContacted = prospect.getLastContactedAt() == null
                && Set.of("CONTACTED", "FOLLOW_UP", "REPLIED", "WON", "LOST").contains(stage);

        prospect.updatePipeline(
                stage,
                request.nextFollowUpAt(),
                request.note(),
                markContacted
        );

        return prospectRepository.save(prospect);
    }

    public CampaignDashboardResponse dashboard(String campaignId) {
        Map<String, DiscoveredProspectEntity> latest = latestByProspect(campaignId);

        int newCount = 0;
        int contactedCount = 0;
        int followUpCount = 0;
        int repliedCount = 0;
        int wonCount = 0;
        int lostCount = 0;
        List<String> dueIds = new ArrayList<>();
        Set<String> categories = new LinkedHashSet<>();
        Instant now = Instant.now();

        for (DiscoveredProspectEntity prospect : latest.values()) {
            categories.add(prospect.getCategory());
            switch (normalizeStage(prospect.getPipelineStage())) {
                case "NEW", "REVIEWED" -> newCount++;
                case "CONTACTED" -> contactedCount++;
                case "FOLLOW_UP" -> followUpCount++;
                case "REPLIED" -> repliedCount++;
                case "WON" -> wonCount++;
                case "LOST" -> lostCount++;
                default -> {
                }
            }

            if (prospect.getNextFollowUpAt() != null
                    && !prospect.getNextFollowUpAt().isAfter(now)
                    && !Set.of("WON", "LOST").contains(normalizeStage(prospect.getPipelineStage()))) {
                dueIds.add(prospect.getId());
            }
        }

        Map<String, Integer> adjustments = new LinkedHashMap<>();
        for (String category : categories) {
            if (category == null || category.isBlank()) continue;
            int adjustment = outcomeLearningService.adjustmentFor(campaignId, category);
            if (adjustment != 0) adjustments.put(category, adjustment);
        }

        return new CampaignDashboardResponse(
                latest.size(),
                newCount,
                contactedCount,
                followUpCount,
                repliedCount,
                wonCount,
                lostCount,
                dueIds.size(),
                dueIds,
                adjustments
        );
    }

    public List<DiscoveredProspectEntity> latestProspects(String campaignId) {
        return new ArrayList<>(latestByProspect(campaignId).values());
    }

    private Map<String, DiscoveredProspectEntity> latestByProspect(String campaignId) {
        List<DiscoveredProspectEntity> history =
                prospectRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);

        Map<String, DiscoveredProspectEntity> latest = new LinkedHashMap<>();
        for (DiscoveredProspectEntity prospect : history) {
            String key = prospect.getProspectKey();
            if (key == null || key.isBlank()) key = prospect.getId();
            latest.putIfAbsent(key, prospect);
        }
        return latest;
    }

    private static String normalizeStage(String value) {
        String stage = value == null ? "NEW" : value.trim().toUpperCase(Locale.ROOT);
        if (!STAGES.contains(stage)) {
            throw new IllegalArgumentException("Unsupported pipeline stage: " + value);
        }
        return stage;
    }
}
