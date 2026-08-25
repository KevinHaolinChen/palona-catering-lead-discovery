package com.palona.cateringleads.model;

import java.util.List;

public record DiscoveryResponse(int count, List<DiscoveryCandidate> candidates) {}
