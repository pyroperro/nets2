package org.example.places.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.places.utils.HttpService;
import org.example.places.model.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class WikiGeoApi {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static CompletableFuture<List<Long>> getNearbyPages(Location location) {

        String url = String.format(
                Locale.US,
                "https://en.wikipedia.org/w/api.php?action=query&list=geosearch&gscoord=%f%%7C%f&gsradius=1000&gslimit=5&format=json",
                location.lat(),
                location.lon()
        );

        return HttpService.get(url)
                .thenApply(body -> {
                    try {
                        JsonNode array = JSON.readTree(body)
                                .get("query")
                                .get("geosearch");

                        List<Long> ids = new ArrayList<>();
                        for (JsonNode node : array) {
                            ids.add(node.get("pageid").asLong());
                        }
                        return ids;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
