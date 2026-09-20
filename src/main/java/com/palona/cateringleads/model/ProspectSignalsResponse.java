package com.palona.cateringleads.model;

public record ProspectSignalsResponse(String prospectId, int baseScore, int profileFit, int publicSignals, int adjustedScore, String summary, String sourceUrl) {}
