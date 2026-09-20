package com.palona.cateringleads.model;

public record PageSnapshot(
        String url,
        int statusCode,
        String text,
        String html
) {}
