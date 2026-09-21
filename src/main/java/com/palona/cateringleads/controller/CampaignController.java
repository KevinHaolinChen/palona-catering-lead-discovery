package com.palona.cateringleads.controller;

import com.palona.cateringleads.model.CampaignCreateRequest;
import com.palona.cateringleads.model.CampaignResponse;
import com.palona.cateringleads.model.DiscoveryRunResponse;
import com.palona.cateringleads.model.GeocodeResponse;
import com.palona.cateringleads.model.RestaurantSearchResult;
import com.palona.cateringleads.model.ProspectOutreachResponse;
import com.palona.cateringleads.model.ProspectInsightResponse;
import com.palona.cateringleads.model.ProspectEvidenceResponse;
import com.palona.cateringleads.persistence.DiscoveredProspectEntity;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import com.palona.cateringleads.service.CampaignService;
import com.palona.cateringleads.service.GeocodingService;
import com.palona.cateringleads.service.ProspectOutreachService;
import com.palona.cateringleads.service.ProspectIntelligenceService;
import com.palona.cateringleads.service.ProspectEvidenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CampaignController {
    private final CampaignService campaignService;
    private final DiscoveredProspectRepository prospectRepository;
    private final GeocodingService geocodingService;
    private final ProspectOutreachService prospectOutreachService;
    private final ProspectIntelligenceService prospectIntelligenceService;
    private final ProspectEvidenceService prospectEvidenceService;

    public CampaignController(
            CampaignService campaignService,
            DiscoveredProspectRepository prospectRepository,
            GeocodingService geocodingService,
            ProspectOutreachService prospectOutreachService,
            ProspectIntelligenceService prospectIntelligenceService,
            ProspectEvidenceService prospectEvidenceService
    ) {
        this.campaignService = campaignService;
        this.prospectRepository = prospectRepository;
        this.geocodingService = geocodingService;
        this.prospectOutreachService = prospectOutreachService;
        this.prospectIntelligenceService = prospectIntelligenceService;
        this.prospectEvidenceService = prospectEvidenceService;
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignResponse create(@Valid @RequestBody CampaignCreateRequest request) {
        return campaignService.create(request);
    }

    @GetMapping("/campaigns")
    public List<CampaignResponse> campaigns() {
        return campaignService.findAll();
    }

    @GetMapping("/restaurants/search")
    public List<RestaurantSearchResult> restaurantSearch(
            @RequestParam("q") String query,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon
    ) {
        return geocodingService.searchRestaurants(query, lat, lon);
    }

    @GetMapping("/geocode")
    public GeocodeResponse geocode(@RequestParam String address) {
        return geocodingService.geocode(address);
    }

    @PostMapping("/campaigns/{campaignId}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DiscoveryRunResponse startRun(@PathVariable String campaignId) {
        return campaignService.startRun(campaignId);
    }

    @GetMapping("/runs/{runId}")
    public DiscoveryRunResponse run(@PathVariable String runId) {
        return campaignService.findRun(runId);
    }

    @GetMapping("/runs/{runId}/prospects")
    public List<DiscoveredProspectEntity> runProspects(@PathVariable String runId) {
        return prospectRepository.findByRunIdOrderByScoreDesc(runId);
    }

    @PostMapping("/discovered-prospects/{prospectId}/evidence")
    public ProspectEvidenceResponse prospectEvidence(@PathVariable String prospectId) {
        return prospectEvidenceService.enrich(prospectId);
    }

    @PostMapping("/discovered-prospects/{prospectId}/insight")
    public ProspectInsightResponse prospectInsight(@PathVariable String prospectId) {
        return prospectIntelligenceService.analyze(prospectId);
    }

    @PostMapping("/discovered-prospects/{prospectId}/outreach")
    public ProspectOutreachResponse prepareOutreach(@PathVariable String prospectId) {
        return prospectOutreachService.prepare(prospectId);
    }

    @GetMapping("/ai/status")
    public Map<String, Object> aiStatus() {
        return Map.of(
                "enabled", prospectIntelligenceService.llmAvailable(),
                "model", prospectIntelligenceService.configuredModel()
        );
    }
}
