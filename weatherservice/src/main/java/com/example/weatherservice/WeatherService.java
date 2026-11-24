package com.example.weatherservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.sharedlib.redis.RedisStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Service
public class WeatherService {
    public record Location(String name, String country, String admin1, double latitude, double longitude) {}
    public record Current(String time, Double temperature, Double feelsLike, Integer humidity,
                          Double windSpeed, Integer weatherCode, boolean isDay) {}
    public record Day(String date, Integer weatherCode, Double min, Double max,
                      Integer precipitationProbability, String sunrise, String sunset) {}
    public record Weather(Location location, String timezone, Current current, List<Day> forecast, String provider) {}
    record Locations(List<Location> results) {}
    record RawCurrent(String time, @JsonProperty("temperature_2m") Double temperature,
                      @JsonProperty("apparent_temperature") Double feelsLike,
                      @JsonProperty("relative_humidity_2m") Integer humidity,
                      @JsonProperty("wind_speed_10m") Double windSpeed,
                      @JsonProperty("weather_code") Integer weatherCode,
                      @JsonProperty("is_day") Integer isDay) {}
    record RawDaily(List<String> time, @JsonProperty("weather_code") List<Integer> codes,
                    @JsonProperty("temperature_2m_min") List<Double> min,
                    @JsonProperty("temperature_2m_max") List<Double> max,
                    @JsonProperty("precipitation_probability_max") List<Integer> rain,
                    List<String> sunrise, List<String> sunset) {}
    record RawWeather(String timezone, RawCurrent current, RawDaily daily) {}

    private final RestClient forecast;
    private final RestClient geocoding;
    private final String apiKey;
    private final RedisStore redis;

    public WeatherService(@Value("${weather.forecast-url}") String forecastUrl,
                          @Value("${weather.geocoding-url}") String geocodingUrl,
                          @Value("${weather.api-key:}") String apiKey, RedisStore redis) {
        this.forecast = client(forecastUrl);
        this.geocoding = client(geocodingUrl);
        this.apiKey = apiKey;
        this.redis = redis;
    }

    private static RestClient client(String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    private UriBuilder key(UriBuilder builder) {
        return apiKey.isBlank() ? builder : builder.queryParam("apikey", apiKey);
    }

    public List<Location> locations(String city) {
        if (city == null || city.trim().length() < 2 || city.trim().length() > 150)
            throw new ResponseStatusException(BAD_REQUEST, "Введите название города: от 2 до 150 символов.");
        String normalized = city.trim().toLowerCase(java.util.Locale.ROOT);
        String key = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.Arrays.asList(redis.cached("weather:cities:" + key, Duration.ofDays(1), Location[].class,
                () -> loadLocations(normalized).toArray(Location[]::new)));
    }

    private List<Location> loadLocations(String city) {
        var result = geocoding.get().uri(b -> key(b.path("/v1/search").queryParam("name", "{city}")
                .queryParam("count", 8).queryParam("language", "ru")).build(city.trim()))
                .retrieve().body(Locations.class);
        if (result == null) throw unavailable();
        return result.results() == null ? List.of() : result.results();
    }

    public Weather weather(String city, Double latitude, Double longitude) {
        boolean hasCity = city != null && !city.isBlank();
        if (hasCity && (latitude != null || longitude != null))
            throw new ResponseStatusException(BAD_REQUEST, "Укажите город или координаты.");
        Location location;
        if (hasCity) {
            var matches = locations(city);
            if (matches.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "Город не найден. Уточните название.");
            location = matches.get(0);
        } else {
            if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180)
                throw new ResponseStatusException(BAD_REQUEST, "Укажите широту от −90 до 90 и долготу от −180 до 180.");
            location = new Location(null, null, null, latitude, longitude);
        }
        var selected = location;
        var cached = redis.cached("weather:forecast:" + selected.latitude() + ":" + selected.longitude(),
                Duration.ofMinutes(10), Weather.class, () -> loadWeather(selected));
        return new Weather(location, cached.timezone(), cached.current(), cached.forecast(), cached.provider());
    }

    private Weather loadWeather(Location location) {
        var selected = location;
        var raw = forecast.get().uri(b -> key(b.path("/v1/forecast")
                .queryParam("latitude", selected.latitude()).queryParam("longitude", selected.longitude())
                .queryParam("timezone", "auto").queryParam("forecast_days", 7).queryParam("wind_speed_unit", "ms")
                .queryParam("current", "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day")
                .queryParam("daily", "weather_code,temperature_2m_min,temperature_2m_max,precipitation_probability_max,sunrise,sunset"))
                .build()).retrieve().body(RawWeather.class);
        if (raw == null || raw.current() == null || raw.timezone() == null || raw.daily() == null) throw unavailable();
        var current = raw.current();
        if (current.time() == null || current.temperature() == null || current.weatherCode() == null) throw unavailable();
        var daily = raw.daily();
        if (daily.time() == null || daily.time().isEmpty()) throw unavailable();
        int count = daily.time().size();
        for (var values : new List<?>[]{daily.codes(), daily.min(), daily.max(), daily.rain(), daily.sunrise(), daily.sunset()})
            if (values == null || values.size() != count) throw unavailable();
        List<Day> days = new ArrayList<>();
        for (int i = 0; i < count; i++) days.add(new Day(daily.time().get(i), daily.codes().get(i), daily.min().get(i),
                daily.max().get(i), daily.rain().get(i), daily.sunrise().get(i), daily.sunset().get(i)));
        return new Weather(location, raw.timezone(), new Current(current.time(), current.temperature(), current.feelsLike(),
                current.humidity(), current.windSpeed(), current.weatherCode(), Integer.valueOf(1).equals(current.isDay())), days, "Open-Meteo");
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(SERVICE_UNAVAILABLE, "Сервис погоды вернул неполные данные. Повторите позже.");
    }
}
