package com.palona.cateringleads.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record Evidence(
        @NotBlank String id,
        @NotNull ClaimKind kind,
        @NotBlank String text,
        @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
        String sourceTitle,
        String sourceUrl,
        String observedAt,
        List<String> supports
) {
    public Evidence {
        supports = supports == null ? List.of() : List.copyOf(supports);
    }
}
