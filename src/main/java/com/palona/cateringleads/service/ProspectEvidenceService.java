package com.palona.cateringleads.service;

import com.palona.cateringleads.model.PageSnapshot;
import com.palona.cateringleads.model.ProspectEvidenceResponse;
import com.palona.cateringleads.persistence.CampaignEntity;
import com.palona.cateringleads.persistence.CampaignRepository;
import com.palona.cateringleads.persistence.DiscoveredProspectEntity;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import com.palona.cateringleads.persistence.DiscoveryRunEntity;
import com.palona.cateringleads.persistence.DiscoveryRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ProspectEvidenceService {

    private static final Set<String> EVENT_TERMS = Set.of(
            "meeting", "conference", "event", "training", "workshop", "seminar"
    );
    private static final Set<String> FOOD_TERMS = Set.of(
            "catering", "lunch", "breakfast", "dinner", "meal", "banquet"
    );
    private static final Set<String> SCALE_TERMS = Set.of(
            "department", "campus", "staff", "employees", "locations", "offices"
    );

    private final DiscoveredProspectRepository prospectRepository;
    private final DiscoveryRunRepository runRepository;
    private final CampaignRepository campaignRepository;
    private final EnrichmentService enrichmentService;

    public ProspectEvidenceService(
            DiscoveredProspectRepository prospectRepository,
            DiscoveryRunRepository runRepository,
            CampaignRepository campaignRepository,
            EnrichmentService enrichmentService
    ) {
        this.prospectRepository = prospectRepository;
        this.runRepository = runRepository;
        this.campaignRepository = campaignRepository;
        this.enrichmentService = enrichmentService;
    }

    @Transactional
    public ProspectEvidenceResponse enrich(String prospectId) {
        DiscoveredProspectEntity prospect = prospectRepository.findById(prospectId)
                .orElseThrow(() -> new IllegalArgumentException("Prospect not found: " + prospectId));
        DiscoveryRunEntity run = runRepository.findById(prospect.getRunId())
                .orElseThrow(() -> new IllegalArgumentException("Discovery run not found: " + prospect.getRunId()));
        CampaignEntity campaign = campaignRepository.findById(run.getCampaignId())
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + run.getCampaignId()));

        int profileFit = profileFit(campaign, prospect);
        EvidenceSignals evidence = evidenceSignals(prospect.getWebsite());

        int adjustedScore = Math.min(
                100,
                (int) Math.round(prospect.getBaseScore() * 0.80)
                        + profileFit
                        + evidence.score()
        );

        prospect.applyEvidence(
                adjustedScore,
                profileFit,
                evidence.score(),
                evidence.summary(),
                evidence.sourceUrl()
        );
        prospectRepository.save(prospect);

        return new ProspectEvidenceResponse(
                prospect.getId(),
                prospect.getBaseScore(),
                profileFit,
                evidence.score(),
                adjustedScore,
                evidence.summary(),
                evidence.sourceUrl()
        );
    }

    private int profileFit(CampaignEntity campaign, DiscoveredProspectEntity prospect) {
        int score = campaign.isSupportsCatering() ? 5 : 1;
        String type = normalize(campaign.getBusinessType());
        String daypart = normalize(campaign.getPrimaryDaypart());
        String priceTier = normalize(campaign.getPriceTier());
        String category = prospect.getCategory();

        boolean office = category != null && (
                category.startsWith("corporate") || "law_office".equals(category)
        );
        boolean institution = Set.of("university_campus", "hospital").contains(category);
        boolean eventSpace = "community_event_space".equals(category);

        if (Set.of("breakfast", "breakfast_lunch").contains(daypart) && (office || institution)) score += 1;
        if (Set.of("lunch", "lunch_dinner", "all_day").contains(daypart) && (office || institution)) score += 1;
        if (Set.of("dinner", "lunch_dinner", "all_day").contains(daypart) && eventSpace) score += 2;

        if (Set.of("pizza", "fast_casual").contains(type) && (office || institution)) score += 1;
        if (Set.of("cafe_bakery", "breakfast_brunch").contains(type) && (office || institution)) score += 1;
        if (Set.of("full_service", "restaurant_catering").contains(type) && (office || eventSpace)) score += 1;

        if ("premium".equals(priceTier) && (office || eventSpace)) score += 1;
        if ("value".equals(priceTier) && institution) score += 1;

        if (prospect.getDistanceMiles() <= campaign.getDeliveryRadiusMiles()) score += 1;
        else score -= 2;

        return Math.max(0, Math.min(10, score));
    }

    private EvidenceSignals evidenceSignals(String website) {
        if (website == null || website.isBlank()) {
            return new EvidenceSignals(
                    0,
                    "No public website was available for evidence extraction.",
                    null
            );
        }

        try {
            PageSnapshot page = enrichmentService.fetchPublicPage(website);
            String text = page.text() == null ? "" : page.text().toLowerCase(Locale.ROOT);

            Set<String> events = matches(text, EVENT_TERMS);
            Set<String> food = matches(text, FOOD_TERMS);
            Set<String> scale = matches(text, SCALE_TERMS);

            int score = Math.min(
                    10,
                    Math.min(4, events.size())
                            + Math.min(3, food.size())
                            + Math.min(2, scale.size())
                            + (!events.isEmpty() && !food.isEmpty() ? 1 : 0)
            );

            List<String> parts = new ArrayList<>();
            if (!events.isEmpty()) parts.add("event signals: " + firstThree(events));
            if (!food.isEmpty()) parts.add("food signals: " + firstThree(food));
            if (!scale.isEmpty()) parts.add("scale signals: " + firstThree(scale));

            String summary = parts.isEmpty()
                    ? "Public site scanned; no strong group-order signals were found."
                    : String.join(" · ", parts);

            return new EvidenceSignals(score, summary, page.url());
        } catch (Exception exception) {
            return new EvidenceSignals(
                    0,
                    "Public website evidence could not be retrieved.",
                    website
            );
        }
    }

    private static Set<String> matches(String text, Set<String> terms) {
        Set<String> found = new LinkedHashSet<>();
        for (String term : terms) {
            if (text.contains(term)) found.add(term);
        }
        return found;
    }

    private static String firstThree(Set<String> values) {
        return String.join(", ", values.stream().limit(3).toList());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record EvidenceSignals(int score, String summary, String sourceUrl) {}
}
