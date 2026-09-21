package com.palona.cateringleads.service;

import com.palona.cateringleads.model.PageSnapshot;
import com.palona.cateringleads.model.ProspectInsightResponse;
import com.palona.cateringleads.persistence.CampaignEntity;
import com.palona.cateringleads.persistence.CampaignRepository;
import com.palona.cateringleads.persistence.DiscoveredProspectEntity;
import com.palona.cateringleads.persistence.DiscoveredProspectRepository;
import com.palona.cateringleads.persistence.DiscoveryRunEntity;
import com.palona.cateringleads.persistence.DiscoveryRunRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProspectIntelligenceService {

    private static final URI OPENAI_RESPONSES_URL = URI.create("https://api.openai.com/v1/responses");
    private static final int MAX_WEBSITE_EVIDENCE_CHARS = 4_000;

    private final DiscoveredProspectRepository prospectRepository;
    private final DiscoveryRunRepository runRepository;
    private final CampaignRepository campaignRepository;
    private final EnrichmentService enrichmentService;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final Map<String, ProspectInsightResponse> cache = new ConcurrentHashMap<>();

    public ProspectIntelligenceService(
            DiscoveredProspectRepository prospectRepository,
            DiscoveryRunRepository runRepository,
            CampaignRepository campaignRepository,
            EnrichmentService enrichmentService,
            JsonMapper jsonMapper
    ) {
        this.prospectRepository = prospectRepository;
        this.runRepository = runRepository;
        this.campaignRepository = campaignRepository;
        this.enrichmentService = enrichmentService;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public ProspectInsightResponse analyze(String prospectId) {
        ProspectInsightResponse cached = cache.get(prospectId);
        if (cached != null) return cached;

        Context context = loadContext(prospectId);
        ProspectInsightResponse result = llmAvailable()
                ? generateWithOpenAi(context)
                : heuristicFallback(context);

        cache.put(prospectId, result);
        return result;
    }

    public boolean llmAvailable() {
        String key = System.getenv("OPENAI_API_KEY");
        return key != null && !key.isBlank();
    }

    public String configuredModel() {
        return System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5.6-luna");
    }

    private Context loadContext(String prospectId) {
        DiscoveredProspectEntity prospect = prospectRepository.findById(prospectId)
                .orElseThrow(() -> new IllegalArgumentException("Prospect not found: " + prospectId));

        DiscoveryRunEntity run = runRepository.findById(prospect.getRunId())
                .orElseThrow(() -> new IllegalArgumentException("Discovery run not found: " + prospect.getRunId()));

        CampaignEntity campaign = campaignRepository.findById(run.getCampaignId())
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + run.getCampaignId()));

        String websiteEvidence = "";
        if (prospect.getWebsite() != null && !prospect.getWebsite().isBlank()) {
            try {
                PageSnapshot page = enrichmentService.fetchPublicPage(prospect.getWebsite());
                websiteEvidence = page.text();
                if (websiteEvidence.length() > MAX_WEBSITE_EVIDENCE_CHARS) {
                    websiteEvidence = websiteEvidence.substring(0, MAX_WEBSITE_EVIDENCE_CHARS);
                }
            } catch (Exception ignored) {
                // The model can still work from structured discovery facts.
            }
        }

        return new Context(campaign, prospect, websiteEvidence);
    }

    private ProspectInsightResponse generateWithOpenAi(Context context) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = configuredModel();

        String prompt = """
                You are the grounded prospect-intelligence layer for Gather Radius, a restaurant B2B growth product.

                Analyze ONE discovered organization as a potential catering/group-order prospect for the restaurant below.
                Return prospect-specific reasoning, not generic sales copy.

                Rules:
                - Use only the supplied facts and website evidence.
                - Never invent employee counts, budgets, events, decision-makers, demand, or contact details.
                - Distinguish possibility from evidence. Use language like "could", "may", or "suggests" when appropriate.
                - Give exactly 3 concise reasons. At least one reason must mention a fact unique to this prospect
                  (name, distance, category, address/location context, contactability, or website evidence).
                - Avoid repeating the same sentence pattern across reasons.
                - The outreach email should be 70-120 words, specific to the prospect, low-pressure, and fact-grounded.
                - Do not claim the organization needs catering unless the evidence explicitly says so.

                Restaurant:
                name=%s
                inferred_type=%s
                origin_address=%s
                supports_catering=%s
                primary_daypart=%s
                price_tier=%s
                delivery_radius_miles=%d

                Prospect:
                name=%s
                category=%s
                address=%s
                distance_miles=%.2f
                website=%s
                phone=%s
                base_score=%d
                adjusted_score=%d
                profile_fit_score=%d
                evidence_score=%d
                evidence_summary=%s
                score_components=%s
                source=%s
                source_url=%s

                Public website evidence (may be empty):
                %s
                """.formatted(
                context.campaign().getName(),
                context.campaign().getBusinessType(),
                context.campaign().getOriginAddress(),
                context.campaign().isSupportsCatering(),
                context.campaign().getPrimaryDaypart(),
                context.campaign().getPriceTier(),
                context.campaign().getDeliveryRadiusMiles(),
                context.prospect().getOrganization(),
                context.prospect().getCategory(),
                context.prospect().getAddress(),
                context.prospect().getDistanceMiles(),
                nullToEmpty(context.prospect().getWebsite()),
                nullToEmpty(context.prospect().getPhone()),
                context.prospect().getBaseScore(),
                context.prospect().getScore(),
                context.prospect().getProfileFitScore(),
                context.prospect().getEvidenceScore(),
                nullToEmpty(context.prospect().getEvidenceSummary()),
                context.prospect().getScoreExplanation(),
                nullToEmpty(context.prospect().getSourceName()),
                nullToEmpty(context.prospect().getSourceUrl()),
                context.websiteEvidence()
        ).trim();

        try {
            Map<String, Object> schema = Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "reasons", Map.of(
                                    "type", "array",
                                    "items", Map.of("type", "string"),
                                    "minItems", 3,
                                    "maxItems", 3
                            ),
                            "outreach_subject", Map.of("type", "string"),
                            "outreach_body", Map.of("type", "string"),
                            "evidence_note", Map.of("type", "string")
                    ),
                    "required", List.of("reasons", "outreach_subject", "outreach_body", "evidence_note"),
                    "additionalProperties", false
            );

            Map<String, Object> format = new LinkedHashMap<>();
            format.put("type", "json_schema");
            format.put("name", "gather_prospect_insight");
            format.put("strict", true);
            format.put("schema", schema);

            Map<String, Object> text = Map.of("format", format);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("input", prompt);
            payload.put("store", false);
            payload.put("text", text);

            HttpRequest request = HttpRequest.newBuilder(OPENAI_RESPONSES_URL)
                    .timeout(Duration.ofSeconds(35))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI returned HTTP " + response.statusCode());
            }

            String outputText = extractOutputText(jsonMapper.readTree(response.body()));
            JsonNode insight = jsonMapper.readTree(outputText);

            List<String> reasons = new java.util.ArrayList<>();
            for (JsonNode reason : insight.get("reasons")) reasons.add(reason.asText());

            return new ProspectInsightResponse(
                    context.prospect().getId(),
                    model,
                    true,
                    reasons,
                    insight.get("outreach_subject").asText(),
                    insight.get("outreach_body").asText(),
                    insight.get("evidence_note").asText()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return heuristicFallback(context, "AI request was interrupted; showing transparent heuristic fallback.");
        } catch (Exception exception) {
            return heuristicFallback(
                    context,
                    "Generative analysis was unavailable (" + rootMessage(exception) + "); showing transparent heuristic fallback."
            );
        }
    }

    private ProspectInsightResponse heuristicFallback(Context context) {
        return heuristicFallback(
                context,
                "OPENAI_API_KEY is not configured. These reasons come from the deterministic score, not generative AI."
        );
    }

    private ProspectInsightResponse heuristicFallback(Context context, String note) {
        DiscoveredProspectEntity p = context.prospect();
        CampaignEntity c = context.campaign();

        String distanceReason = p.getDistanceMiles() <= 2
                ? "%s is only %.1f miles from %s, which improves local delivery practicality."
                    .formatted(p.getOrganization(), p.getDistanceMiles(), c.getName())
                : "%s is %.1f miles from %s and falls inside the selected Radius."
                    .formatted(p.getOrganization(), p.getDistanceMiles(), c.getName());

        String categoryReason = switch (p.getCategory()) {
            case "corporate_office", "corporate_hq", "corporate_campus" ->
                    "Its office profile can create plausible team-meal, meeting, and visitor-order occasions.";
            case "university_campus" ->
                    "Its campus profile can create plausible department, training, and event-related group orders.";
            case "hospital" ->
                    "Its hospital profile suggests many departments and staff-meeting occasions, subject to outside-vendor rules.";
            case "community_event_space" ->
                    "Its event/community-space profile directly involves group gatherings where food service may be relevant.";
            default ->
                    "Its organization profile is a plausible local group-order lead, but stronger evidence is still needed.";
        };

        String contactReason = (p.getWebsite() != null && !p.getWebsite().isBlank())
                || (p.getPhone() != null && !p.getPhone().isBlank())
                ? "A public website or phone path is available for verification and outreach."
                : "Public contact data is sparse, so this prospect needs additional enrichment before outreach.";

        return new ProspectInsightResponse(
                p.getId(),
                "heuristic",
                false,
                List.of(distanceReason, categoryReason, contactReason),
                "Local catering / group-order options from " + c.getName(),
                deterministicOutreach(c, p),
                note
        );
    }

    private static String deterministicOutreach(CampaignEntity campaign, DiscoveredProspectEntity prospect) {
        return """
                Hi %s team,

                I'm reaching out from %s. You're about %.1f miles from our location, and I wanted to introduce us as a nearby option for group meals, meetings, or events when those needs come up.

                I'd be happy to send over a concise menu, pricing, and delivery details if useful.

                Who would be the best person to speak with about group food orders?

                Best,
                %s
                """.formatted(
                prospect.getOrganization(),
                campaign.getName(),
                prospect.getDistanceMiles(),
                campaign.getName()
        ).trim();
    }

    private static String extractOutputText(JsonNode root) {
        JsonNode output = root.get("output");
        if (output == null || !output.isArray()) {
            throw new IllegalStateException("OpenAI response did not contain output");
        }
        StringBuilder result = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.get("content");
            if (content == null || !content.isArray()) continue;
            for (JsonNode part : content) {
                if ("output_text".equals(part.path("type").asText()) && !part.path("text").isMissingNode()) {
                    if (!result.isEmpty()) result.append('\n');
                    result.append(part.get("text").asText());
                }
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("OpenAI response contained no output_text");
        return result.toString().trim();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String value = current.getMessage();
        return value == null || value.isBlank() ? current.getClass().getSimpleName() : value.replaceAll("\\s+", " ").trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Context(
            CampaignEntity campaign,
            DiscoveredProspectEntity prospect,
            String websiteEvidence
    ) {}
}
