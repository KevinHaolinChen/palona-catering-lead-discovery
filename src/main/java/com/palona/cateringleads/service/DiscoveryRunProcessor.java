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
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DiscoveryRunProcessor {
    private final CampaignRepository campaignRepository;
    private final DiscoveryRunRepository runRepository;
    private final DiscoveredProspectRepository prospectRepository;
    private final List<ProspectSource> sources;

    public DiscoveryRunProcessor(CampaignRepository campaignRepository, DiscoveryRunRepository runRepository,
                                 DiscoveredProspectRepository prospectRepository, List<ProspectSource> sources) {
        this.campaignRepository = campaignRepository;
        this.runRepository = runRepository;
        this.prospectRepository = prospectRepository;
        this.sources = sources;
    }

    @Async
    @Transactional
    public void processAsync(String runId, String campaignId) {
        DiscoveryRunEntity run = runRepository.findById(runId).orElseThrow();
        try {
            CampaignEntity campaign = campaignRepository.findById(campaignId).orElseThrow();
            run.markRunning();
            runRepository.save(run);

            SearchCriteria criteria = new SearchCriteria(campaign.getLatitude(), campaign.getLongitude(), campaign.getRadiusMeters());
            List<DiscoveryCandidate> discovered = new ArrayList<>();
            for (ProspectSource source : sources) discovered.addAll(source.discover(criteria));

            Map<String, DiscoveryCandidate> deduped = new LinkedHashMap<>();
            discovered.stream()
                    .sorted(Comparator.comparingInt(DiscoveryCandidate::score).reversed())
                    .forEach(candidate -> deduped.putIfAbsent(dedupeKey(candidate), candidate));

            List<DiscoveredProspectEntity> entities = deduped.values().stream()
                    .map(candidate -> DiscoveredProspectEntity.from(runId, candidate))
                    .toList();
            prospectRepository.saveAll(entities);

            run.markCompleted(entities.size());
            runRepository.save(run);
        } catch (Exception exception) {
            run.markFailed(exception.getMessage());
            runRepository.save(run);
        }
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
