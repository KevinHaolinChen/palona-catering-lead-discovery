package com.palona.cateringleads.service;

import com.palona.cateringleads.model.ClaimKind;
import com.palona.cateringleads.model.Evidence;
import com.palona.cateringleads.model.Prospect;
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

@Service
public class OutreachService {

    private static final URI OPENAI_RESPONSES_URL = URI.create("https://api.openai.com/v1/responses");

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;

    public OutreachService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String deterministicOutreach(Prospect prospect) {
        return prospect.outreachDraft();
    }

    public boolean llmAvailable() {
        String key = System.getenv("OPENAI_API_KEY");
        return key != null && !key.isBlank();
    }

    /**
     * Optional grounded LLM generation. The project remains fully reviewable without an API key.
     */
    public String generateWithOpenAi(Prospect prospect) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }
        String model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-5.6");

        List<String> facts = prospect.evidence().stream()
                .filter(evidence -> evidence.kind() == ClaimKind.FACT)
                .map(Evidence::text)
                .toList();
        List<String> inferences = prospect.evidence().stream()
                .filter(evidence -> evidence.kind() == ClaimKind.INFERENCE)
                .map(Evidence::text)
                .toList();

        String prompt = """
                Write a concise B2B catering outreach email body (70-110 words) from a nearby restaurant offering group orders or catering.
                Use ONLY the evidence below. Do not invent names, employee counts, events, budgets, or needs.
                If evidence is weak, phrase the offer conditionally. No hype.

                Organization: %s
                Public facts: %s
                Explicit inferences: %s
                Target role: %s
                """.formatted(prospect.organization(), facts, inferences, prospect.contact().role()).trim();

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("input", prompt);
            String requestBody = jsonMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder(OPENAI_RESPONSES_URL)
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenAI returned HTTP " + response.statusCode());
            }
            return extractOutputText(jsonMapper.readTree(response.body()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM request was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("LLM generation failed", exception);
        }
    }

    private static String extractOutputText(JsonNode root) {
        JsonNode output = root.get("output");
        if (output == null || !output.isArray()) {
            throw new IllegalStateException("OpenAI response did not contain output");
        }
        StringBuilder result = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.get("content");
            if (content == null || !content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                JsonNode type = part.get("type");
                JsonNode text = part.get("text");
                if (type != null && "output_text".equals(type.asText()) && text != null) {
                    if (!result.isEmpty()) {
                        result.append('\n');
                    }
                    result.append(text.asText());
                }
            }
        }
        String value = result.toString().trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("OpenAI response contained no output_text");
        }
        return value;
    }
}
