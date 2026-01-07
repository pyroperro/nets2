package org.example.places.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.places.utils.HttpService;
import org.example.places.model.Location;
import org.example.places.model.Weather;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class WeatherApi {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API_KEY = "";

    public static CompletableFuture<Weather> getWeather(Location location) {

        String url = String.format(
                Locale.US,
                "https://api.openweathermap.org/data/2.5/weather?lat=%f&lon=%f&units=metric&appid=%s",
                location.lat(),
                location.lon(),
                API_KEY
        );

        return HttpService.get(url)
                .thenApply(body -> {
                    try {
                        // System.out.println(body);
                        JsonNode root = JSON.readTree(body);
                        return new Weather(
                                root.get("weather").get(0).get("description").asText(),
                                root.get("main").get("temp").asDouble()
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
