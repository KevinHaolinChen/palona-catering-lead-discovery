package com.palona.cateringleads.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EvidenceBatchService {

    private final ProspectEvidenceService prospectEvidenceService;

    public EvidenceBatchService(ProspectEvidenceService prospectEvidenceService) {
        this.prospectEvidenceService = prospectEvidenceService;
    }

    @Async
    public void enrichAsync(List<String> prospectIds) {
        prospectIds.parallelStream().forEach(prospectId -> {
            try {
                prospectEvidenceService.enrich(prospectId);
            } catch (Exception ignored) {
                // Evidence enrichment is opportunistic; discovery itself remains usable.
            }
        });
    }
}
