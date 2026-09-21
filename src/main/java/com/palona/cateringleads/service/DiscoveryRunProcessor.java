package com.palona.cateringleads.service;

import com.palona.cateringleads.model.DiscoveryCandidate;
import com.palona.cateringleads.model.SearchCriteria;
import com.palona.cateringleads.persistence.CampaignEntity;
import com.palona.cateringleads.persistence.CampaignRepository;
import com.palona.cateringleads.persistence.DiscoveredProspectEntity;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import com.palona.cateringleads.persistence.DiscoveryRunEntity;
import com.palona.cateringleads.persistence.DiscoveryRunRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DiscoveryRunProcessor {
    private static final int ENOUGH_RESULTS = 15;

    private final CampaignRepository campaignRepository;
    private final DiscoveryRunRepository runRepository;
    private final DiscoveredProspectRepository prospectRepository;
    private final OutcomeLearningService outcomeLearningService;
    private final EvidenceBatchService evidenceBatchService;
    private final List<ProspectSource> sources;

    public DiscoveryRunProcessor(
            CampaignRepository campaignRepository,
            DiscoveryRunRepository runRepository,
            DiscoveredProspectRepository prospectRepository,
            OutcomeLearningService outcomeLearningService,
            EvidenceBatchService evidenceBatchService,
            List<ProspectSource> sources
    ) {
        this.campaignRepository = campaignRepository;
        this.runRepository = runRepository;
        this.prospectRepository = prospectRepository;
        this.outcomeLearningService = outcomeLearningService;
        this.evidenceBatchService = evidenceBatchService;
        this.sources = sources;
    }

    @Async
    public void processAsync(String runId, String campaignId) {
        DiscoveryRunEntity run = runRepository.findById(runId).orElseThrow();
        try {
            CampaignEntity campaign = campaignRepository.findById(campaignId).orElseThrow();
            run.markRunning();
            runRepository.save(run);

            SearchCriteria criteria = new SearchCriteria(
                    campaign.getLatitude(),
                    campaign.getLongitude(),
                    campaign.getRadiusMeters()
            );

            List<DiscoveryCandidate> discovered = new ArrayList<>();
            List<String> failures = new ArrayList<>();
            boolean anySourceCompleted = false;

            for (ProspectSource source : sources) {
                try {
                    List<DiscoveryCandidate> candidates = source.discover(criteria);
                    anySourceCompleted = true;
                    discovered.addAll(candidates);
                    if (discovered.size() >= ENOUGH_RESULTS) break;
                } catch (Exception exception) {
                    failures.add(source.sourceName() + ": " + rootMessage(exception));
                }
            }

            if (!anySourceCompleted && discovered.isEmpty()) {
                throw new IllegalStateException(
                        "Discovery providers are unavailable. " + String.join(" | ", failures)
                );
            }

            Map<String, DiscoveryCandidate> deduped = new LinkedHashMap<>();
            discovered.stream()
                    .sorted(Comparator.comparingInt(DiscoveryCandidate::score).reversed())
                    .forEach(candidate -> deduped.putIfAbsent(dedupeKey(candidate), candidate));

            List<DiscoveredProspectEntity> entities = deduped.values().stream()
                    .map(candidate -> {
                        String prospectKey = dedupeKey(candidate);
                        DiscoveredProspectEntity previous = prospectRepository
                                .findByCampaignIdAndProspectKeyOrderByCreatedAtDesc(campaignId, prospectKey)
                                .stream()
                                .findFirst()
                                .orElse(null);
                        int learnedAdjustment =
                                outcomeLearningService.adjustmentFor(campaignId, candidate.category());
                        return DiscoveredProspectEntity.from(
                                runId,
                                campaignId,
                                prospectKey,
                                candidate,
                                previous,
                                learnedAdjustment
                        );
                    })
                    .sorted(Comparator.comparingInt(DiscoveredProspectEntity::getScore).reversed()
                            .thenComparingDouble(DiscoveredProspectEntity::getDistanceMiles))
                    .toList();
            prospectRepository.saveAll(entities);

            List<String> evidenceTargets = entities.stream()
                    .limit(10)
                    .map(DiscoveredProspectEntity::getId)
                    .toList();
            evidenceBatchService.enrichAsync(evidenceTargets);

            campaign.markRefreshed(java.time.Instant.now());
            campaignRepository.save(campaign);

            run.markCompleted(entities.size());
            runRepository.save(run);
        } catch (Exception exception) {
            run.markFailed(rootMessage(exception));
            runRepository.save(run);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message.replaceAll("\\s+", " ").trim();
    }

    private static String dedupeKey(DiscoveryCandidate candidate) {
        String website = candidate.website() == null ? "" : candidate.website().toLowerCase(Locale.ROOT)
                .replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceAll("/+$", "");
        if (!website.isBlank()) return "domain:" + website;
        return "name-address:" + canonical(candidate.name()) + "|" + canonical(candidate.address());
    }

    private static String canonical(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
