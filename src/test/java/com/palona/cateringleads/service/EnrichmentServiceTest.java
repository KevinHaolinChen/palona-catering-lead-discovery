package com.palona.cateringleads.service;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrichmentServiceTest {
    @Test
    void rejectsLoopbackTargets() {
        assertThatThrownBy(() -> EnrichmentService.validatePublicUri(URI.create("http://127.0.0.1/admin")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPrivateNetworkTargets() {
        assertThatThrownBy(() -> EnrichmentService.validatePublicUri(URI.create("http://10.0.0.1/internal")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}