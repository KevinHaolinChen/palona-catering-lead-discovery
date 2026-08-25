package com.palona.cateringleads.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record ContactPath(
        @NotBlank String role,
        @NotBlank String channel,
        String value,
        String sourceUrl,
        @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
        String note
) {}
