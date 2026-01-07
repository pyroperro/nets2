package org.example.places.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.places.model.Location;
import org.example.places.utils.HttpService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GeoCodingApi {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API_KEY = "";

    public static CompletableFuture<List<Location>> search(String query) {

        String url =
                "https://graphhopper.com/api/1/geocode?q=" +
                        URLEncoder.encode(query, StandardCharsets.UTF_8) +
                        "&limit=5&key=" + API_KEY;

        return HttpService.get(url)
                .thenApply(body -> {
                    try {
                        JsonNode hits = JSON.readTree(body).get("hits");
                        List<Location> locations = new ArrayList<>();

                        for (JsonNode hit : hits) {
                            locations.add(new Location(
                                    hit.get("name").asText(),
                                    hit.get("point").get("lat").asDouble(),
                                    hit.get("point").get("lng").asDouble()
                            ));
                        }
                        return locations;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}