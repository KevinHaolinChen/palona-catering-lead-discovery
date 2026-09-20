package com.palona.cateringleads.service;

import com.palona.cateringleads.model.DiscoveryCandidate;
import com.palona.cateringleads.model.EvidenceAssessment;
import com.palona.cateringleads.model.PageSnapshot;
import com.palona.cateringleads.model.ScoreBreakdown;
import com.palona.cateringleads.persistence.CampaignEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EvidenceEngineService {

    private final EnrichmentService enrichmentService;

    public EvidenceEngineService(EnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    public EvidenceAssessment assess(CampaignEntity campaign, DiscoveryCandidate candidate) {
        int profile = profileFit(campaign, candidate);
        EvidenceSignals evidence = scanWebsite(candidate.website());

        ScoreBreakdown base = candidate.scoreBreakdown();
        ScoreBreakdown finalBreakdown = new ScoreBreakdown(
                base.proximity(),
                base.organizationScale(),
                base.eventMeetingSignal(),
                base.foodNeedSignal(),
                base.contactability(),
                profile,
                evidence.score()
        );

        return new EvidenceAssessment(
                profile,
                evidence.score(),
                finalBreakdown.total(),
                evidence.summary(),
                evidence.sourceUrl()
        );
    }

    private EvidenceSignals scanWebsite(String website) {
        if (website == null || website.isBlank()) {
            return new EvidenceSignals(0, "No public website available for evidence scan.", null);
        }

        try {
            PageSnapshot page = enrichmentService.fetchPublicPage(website);
            String text = page.text() == null ? "" : page.text().toLowerCase(Locale.ROOT);

            Set<String> events = matches(text, Set.of(
                    "meeting", "conference", "event", "training", "workshop", "seminar"
            ));
            Set<String> food = matches(text, Set.of(
                    "catering", "lunch", "breakfast", "dinner", "meal", "banquet"
            ));
            Set<String> scale = matches(text, Set.of(
                    "department", "campus", "staff", "employees", "locations", "offices"
            ));

            int score = Math.min(10,
                    Math.min(4, events.size())
                            + Math.min(3, food.size())
                            + Math.min(2, scale.size())
                            + (!events.isEmpty() && !food.isEmpty() ? 1 : 0));

            List<String> parts = new ArrayList<>();
            if (!events.isEmpty()) parts.add("event signals: " + joinFirst(events));
            if (!food.isEmpty()) parts.add("food signals: " + joinFirst(food));
            if (!scale.isEmpty()) parts.add("scale signals: " + joinFirst(scale));

            String summary = parts.isEmpty()
                    ? "Public site scanned; no strong group-order signals found."
                    : String.join(" · ", parts);
            return new EvidenceSignals(score, summary, page.url());
        } catch (Exception exception) {
            return new EvidenceSignals(0, "Public website evidence could not be retrieved.", website);
        }
    }

    private static int profileFit(CampaignEntity campaign, DiscoveryCandidate candidate) {
        String type = campaign.getBusinessType() == null
                ? ""
                : campaign.getBusinessType().toLowerCase(Locale.ROOT);
        String category = candidate.category();
        int score = campaign.isSupportsCatering() ? 6 : 2;

        boolean office = category != null && category.startsWith("corporate");
        boolean institution = Set.of("university_campus", "hospital").contains(category);
        boolean eventSpace = "community_event_space".equals(category);

        if (Set.of("pizza", "fast_casual").contains(type) && (office || institution)) score += 2;
        if (Set.of("cafe_bakery", "breakfast_brunch").contains(type) && (office || institution)) score += 2;
        if (Set.of("full_service", "restaurant_catering").contains(type) && (office || eventSpace)) score += 2;

        if (candidate.distanceMiles() <= campaign.getDeliveryRadiusMiles()) score += 2;
        else score -= 2;

        return Math.max(0, Math.min(10, score));
    }

    private static Set<String> matches(String text, Set<String> terms) {
        Set<String> found = new LinkedHashSet<>();
        for (String term : terms) {
            if (text.contains(term)) found.add(term);
        }
        return found;
    }

    private static String joinFirst(Set<String> values) {
        return String.join(", ", values.stream().limit(3).toList());
    }

    private record EvidenceSignals(int score, String summary, String sourceUrl) {}
}
