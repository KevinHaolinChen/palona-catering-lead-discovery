package com.palona.cateringleads.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ScoreBreakdown(
        @Min(0) @Max(25) int proximity,
        @Min(0) @Max(20) int organizationScale,
        @Min(0) @Max(25) int eventMeetingSignal,
        @Min(0) @Max(20) int foodNeedSignal,
        @Min(0) @Max(10) int contactability
) {
    public int total() {
        return proximity + organizationScale + eventMeetingSignal + foodNeedSignal + contactability;
    }
}
