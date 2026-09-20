package com.palona.cateringleads.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ScoreBreakdown(
        @Min(0) @Max(25) int proximity,
        @Min(0) @Max(15) int organizationScale,
        @Min(0) @Max(20) int eventMeetingSignal,
        @Min(0) @Max(15) int foodNeedSignal,
        @Min(0) @Max(10) int contactability,
        @Min(0) @Max(10) int profileFit,
        @Min(0) @Max(10) int evidence
) {
    public int total() {
        return Math.min(100, proximity + organizationScale + eventMeetingSignal + foodNeedSignal + contactability + profileFit + evidence);
    }
}
