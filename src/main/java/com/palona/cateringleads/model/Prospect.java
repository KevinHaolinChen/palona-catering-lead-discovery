package com.palona.cateringleads.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record Prospect(
        @NotBlank String id,
        @NotBlank String organization,
        @NotBlank String category,
        @NotBlank String address,
        @NotBlank String proximityBand,
        String website,
        @Min(0) @Max(100) int score,
        @NotNull @Valid ScoreBreakdown scoreBreakdown,
        @NotBlank String fitSummary,
        @NotNull @Size(min = 1) List<@Valid Evidence> evidence,
        @NotNull @Valid ContactPath contact,
        @NotBlank String outreachDraft,
        @NotBlank String outreachGeneratedBy,
        List<String> uncertainties
) {
    public Prospect {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
    }
}
