package com.palona.cateringleads.controller;

import com.palona.cateringleads.model.DiscoveryRequest;
import com.palona.cateringleads.model.DiscoveryResponse;
import com.palona.cateringleads.model.OutreachResponse;
import com.palona.cateringleads.model.Prospect;
import com.palona.cateringleads.service.DiscoveryService;
import com.palona.cateringleads.service.OutreachService;
import com.palona.cateringleads.service.ProspectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
public class ProspectController {

    private final ProspectService prospectService;
    private final DiscoveryService discoveryService;
    private final OutreachService outreachService;

    public ProspectController(
            ProspectService prospectService,
            DiscoveryService discoveryService,
            OutreachService outreachService
    ) {
        this.prospectService = prospectService;
        this.discoveryService = discoveryService;
        this.outreachService = outreachService;
    }

    @GetMapping("/api/prospects")
    public List<Prospect> prospects() {
        return prospectService.findAll();
    }

    @GetMapping("/api/prospects/{prospectId}")
    public Prospect prospectDetail(@PathVariable String prospectId) {
        try {
            return prospectService.findById(prospectId);
        } catch (ProspectService.ProspectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prospect not found", exception);
        }
    }

    @PostMapping("/api/discover")
    public DiscoveryResponse discover(@Valid @RequestBody DiscoveryRequest request) {
        try {
            var candidates = discoveryService.discover(
                    request.resolvedLatitude(),
                    request.resolvedLongitude(),
                    request.resolvedRadiusMeters()
            );
            return new DiscoveryResponse(candidates.size(), candidates);
        } catch (Exception exception) {
            String detail = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "Discovery provider failed."
                    : exception.getMessage();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail, exception);
        }
    }

    @PostMapping("/api/prospects/{prospectId}/outreach")
    public OutreachResponse regenerateOutreach(
            @PathVariable String prospectId,
            @RequestParam(defaultValue = "true") boolean useLlm
    ) {
        Prospect prospect;
        try {
            prospect = prospectService.findById(prospectId);
        } catch (ProspectService.ProspectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Prospect not found", exception);
        }

        if (useLlm && outreachService.llmAvailable()) {
            try {
                return new OutreachResponse(
                        prospectId,
                        "openai",
                        outreachService.generateWithOpenAi(prospect),
                        null
                );
            } catch (Exception exception) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "LLM generation failed", exception);
            }
        }

        return new OutreachResponse(
                prospectId,
                "cached_grounded_draft",
                outreachService.deterministicOutreach(prospect),
                "Set OPENAI_API_KEY to enable optional LLM regeneration."
        );
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "cached_prospects", prospectService.findAll().size());
    }
}
