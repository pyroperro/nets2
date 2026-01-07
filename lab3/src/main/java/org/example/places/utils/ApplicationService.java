package org.example.places.utils;

import org.example.places.api.*;
import org.example.places.model.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ApplicationService {

    public static CompletableFuture<List<Location>> findLocations(String query) {
        return GeoCodingApi.search(query);
    }

    public static CompletableFuture<Result> buildResult(Location location) {

        CompletableFuture<Weather> weatherFuture =
                WeatherApi.getWeather(location);

        CompletableFuture<List<Place>> placesFuture =
                WikiGeoApi.getNearbyPages(location)
                        .thenCompose(ids -> {

                            List<CompletableFuture<Place>> futures =
                                    ids.stream()
                                            .map(WikiTextApi::getDescription)
                                            .toList();

                            return CompletableFuture
                                    .allOf(futures.toArray(new CompletableFuture[0]))
                                    .thenApply(v ->
                                            futures.stream()
                                                    .map(CompletableFuture::join)
                                                    .toList()
                                    );
                        });

        return weatherFuture.thenCombine(
                placesFuture,
                (weather, places) ->
                        new Result(location, weather, places)
        );
    }
}