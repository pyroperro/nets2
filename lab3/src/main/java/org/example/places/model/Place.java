package org.example.places.model;

public record Place(
        long pageId,
        String title,
        String description
) {}