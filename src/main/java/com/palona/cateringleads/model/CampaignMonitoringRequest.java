package com.palona.cateringleads.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CampaignMonitoringRequest(
        boolean enabled,
        @Min(1) @Max(30) int intervalDays
) {}
