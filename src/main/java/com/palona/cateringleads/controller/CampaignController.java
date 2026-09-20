package com.palona.cateringleads.controller;

import com.palona.cateringleads.model.CampaignCreateRequest;
import com.palona.cateringleads.model.CampaignResponse;
import com.palona.cateringleads.model.DiscoveryRunResponse;
import com.palona.cateringleads.model.GeocodeResponse;
import com.palona.cateringleads.model.RestaurantSearchResult;
import com.palona.cateringleads.persistence.DiscoveredProspectEntity;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import com.palona.cateringleads.service.CampaignService;
import com.palona.cateringleads.service.GeocodingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CampaignController {
    private final CampaignService campaignService;
    private final DiscoveredProspectRepository prospectRepository;
    private final GeocodingService geocodingService;

    public CampaignController(
            CampaignService campaignService,
            DiscoveredProspectRepository prospectRepository,
            GeocodingService geocodingService
    ) {
        this.campaignService = campaignService;
        this.prospectRepository = prospectRepository;
        this.geocodingService = geocodingService;
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
    public List<RestaurantSearchResult> restaurantSearch(@RequestParam("q") String query) {
        return geocodingService.searchRestaurants(query);
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
}
