package com.palona.cateringleads.model;

public record RestaurantSearchResult(
        String name,
        String address,
        double latitude,
        double longitude,
        String category,
        String provider
) {}
