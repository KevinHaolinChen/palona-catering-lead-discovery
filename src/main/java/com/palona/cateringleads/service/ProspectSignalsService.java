package com.palona.cateringleads.service;

import com.palona.cateringleads.model.ProspectSignalsResponse;
import com.palona.cateringleads.persistence.CampaignRepository;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import com.palona.cateringleads.persistence.DiscoveryRunRepository;
import org.springframework.stereotype.Service;

@Service
public class ProspectSignalsService {
    private final DiscoveredProspectRepository prospectRepository;
    private final DiscoveryRunRepository runRepository;
    private final CampaignRepository campaignRepository;
    private final EnrichmentService enrichmentService;

    public ProspectSignalsService(
            DiscoveredProspectRepository prospectRepository,
            DiscoveryRunRepository runRepository,
            CampaignRepository campaignRepository
    ) {
        this.prospectRepository = prospectRepository;
        this.runRepository = runRepository;
        this.campaignRepository = campaignRepository;
    }
}
