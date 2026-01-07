package org.example.places.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.places.utils.HttpService;
import org.example.places.model.Place;

import java.util.concurrent.CompletableFuture;

public class WikiTextApi {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static CompletableFuture<Place> getDescription(long pageId) {

        String url =
                "https://en.wikipedia.org/w/api.php?action=query&pageids=" +
                        pageId +
                        "&prop=extracts&exintro=true&explaintext=true&format=json";

        return HttpService.get(url)
                .thenApply(body -> {
                    try {
                        JsonNode page = JSON.readTree(body)
                                .get("query")
                                .get("pages")
                                .elements()
                                .next();

                        return new Place(
                                pageId,
                                page.get("title").asText(),
                                page.get("extract").asText()
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
