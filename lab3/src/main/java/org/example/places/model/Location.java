package org.example.places.model;

public record Location(
        String name,
        double lat,
        double lon
) {}