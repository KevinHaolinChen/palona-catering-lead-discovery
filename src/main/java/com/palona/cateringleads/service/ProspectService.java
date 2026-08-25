package com.palona.cateringleads.service;

import com.palona.cateringleads.model.ClaimKind;
import com.palona.cateringleads.model.Evidence;
import com.palona.cateringleads.model.Prospect;
import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class ProspectService {

    private final JsonMapper jsonMapper;
    private final Validator validator;
    private List<Prospect> cachedProspects = List.of();

    public ProspectService(JsonMapper jsonMapper, Validator validator) {
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    @PostConstruct
    void loadAndValidate() {
        try (InputStream input = new ClassPathResource("data/example_prospects.json").getInputStream()) {
            List<Prospect> loaded = jsonMapper.readValue(input, new TypeReference<List<Prospect>>() {});
            loaded.forEach(this::validateProspect);
            cachedProspects = loaded.stream()
                    .sorted(Comparator.comparingInt(Prospect::score).reversed())
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load cached prospect data", exception);
        }
    }

    public List<Prospect> findAll() {
        return cachedProspects;
    }

    public Prospect findById(String id) {
        return cachedProspects.stream()
                .filter(prospect -> prospect.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ProspectNotFoundException(id));
    }

    private void validateProspect(Prospect prospect) {
        Set<ConstraintViolation<Prospect>> violations = validator.validate(prospect);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                    .sorted()
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("unknown validation error");
            throw new IllegalStateException("Invalid cached prospect " + prospect.id() + ": " + message);
        }

        if (prospect.score() != prospect.scoreBreakdown().total()) {
            throw new IllegalStateException("Score mismatch for " + prospect.id());
        }

        for (Evidence evidence : prospect.evidence()) {
            if (evidence.kind() == ClaimKind.FACT
                    && (evidence.sourceUrl() == null || evidence.sourceUrl().isBlank())) {
                throw new IllegalStateException(
                        "Fact without provenance for " + prospect.id() + ": " + evidence.id());
            }
        }
    }

    public static class ProspectNotFoundException extends RuntimeException {
        public ProspectNotFoundException(String id) {
            super("Prospect not found: " + id);
        }
    }
}
