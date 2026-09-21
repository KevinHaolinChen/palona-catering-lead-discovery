package com.palona.cateringleads.service;

import com.palona.cateringleads.persistence.DiscoveredProspectEntity;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OutcomeLearningService {

    private final DiscoveredProspectRepository prospectRepository;

    public OutcomeLearningService(DiscoveredProspectRepository prospectRepository) {
        this.prospectRepository = prospectRepository;
    }

    public int adjustmentFor(String campaignId, String category) {
        if (campaignId == null || category == null) return 0;

        List<DiscoveredProspectEntity> history =
                prospectRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId);

        Map<String, DiscoveredProspectEntity> latestByProspect = new LinkedHashMap<>();
        for (DiscoveredProspectEntity prospect : history) {
            String key = prospect.getProspectKey();
            if (key == null || key.isBlank()) key = prospect.getId();
            latestByProspect.putIfAbsent(key, prospect);
        }

        int wins = 0;
        int replies = 0;
        int losses = 0;

        for (DiscoveredProspectEntity prospect : latestByProspect.values()) {
            if (!category.equalsIgnoreCase(prospect.getCategory())) continue;

            switch (normalize(prospect.getPipelineStage())) {
                case "WON" -> wins++;
                case "REPLIED" -> replies++;
                case "LOST" -> losses++;
                default -> {
                }
            }
        }

        int outcomes = wins + replies + losses;
        if (outcomes == 0) return 0;

        double signal = ((wins * 5.0) + (replies * 2.0) - (losses * 3.0)) / outcomes;
        return Math.max(-8, Math.min(8, (int) Math.round(signal)));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
