package org.example.places.model;

import java.util.List;

public record Result(
        Location location,
        Weather weather,
        List<Place> places
) {}