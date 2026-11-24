package com.example.weatherservice;

import com.sun.net.httpserver.HttpServer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "security.jwt.secret=test-only-signing-key-at-least-32-bytes-long")
@AutoConfigureMockMvc
@org.junit.jupiter.api.Tag("integration")
class WeatherApiTest {
    static final HttpServer provider;
    static volatile int upstreamStatus = 200;
    static volatile String cityQuery;
    static volatile String forecastQuery;
    static volatile String locations;
    static volatile String forecast;
    static {
        try {
            provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provider.createContext("/v1/", exchange -> {
                boolean search = exchange.getRequestURI().getPath().endsWith("search");
                if (search) cityQuery = exchange.getRequestURI().getRawQuery();
                else forecastQuery = exchange.getRequestURI().getRawQuery();
                byte[] body = (search ? locations : forecast).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(upstreamStatus, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            provider.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + provider.getAddress().getPort();
        registry.add("weather.forecast-url", () -> url);
        registry.add("weather.geocoding-url", () -> url);
    }
    @AfterAll static void stop() { provider.stop(0); }
    @Autowired MockMvc mvc;
    @Autowired SecretKey jwtSigningKey;
    @BeforeEach void fixtures() {
        upstreamStatus = 200; cityQuery = null; forecastQuery = null;
        locations = """
                {"results":[{"name":"Москва","country":"Россия","admin1":"Москва","latitude":55.75,"longitude":37.62,"extra":"ignored"}]}
                """;
        forecast = """
                {"timezone":"Europe/Moscow","current":{"time":"2026-11-24T09:00","temperature_2m":-2.5,
                "apparent_temperature":-5,"relative_humidity_2m":90,"wind_speed_10m":2.5,"weather_code":3,"is_day":1},
                "daily":{"time":["2026-11-24","2026-11-25"],"weather_code":[71,3],"temperature_2m_min":[-3,-4],
                "temperature_2m_max":[0,1],"precipitation_probability_max":[80,10],
                "sunrise":["2026-11-24T08:22","2026-11-25T08:24"],"sunset":["2026-11-24T16:09","2026-11-25T16:08"]}}
                """;
    }
    String token() {
        var claims = JwtClaimsSet.builder().subject("1").issuer("travelapp").audience(List.of("travelapp-api"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer " + new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey)).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    @Test void requiresValidJwt() throws Exception {
        mvc.perform(get("/weather").param("city", "Москва")).andExpect(status().isUnauthorized());
        mvc.perform(get("/weather/locations").param("city", "Москва").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        assertThat(cityQuery).isNull();
    }
    @Test void citySearchEncodesInputAndReturnsNames() throws Exception {
        mvc.perform(get("/weather/locations").param("city", "Москва&count=100").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Москва"));
        assertThat(cityQuery).contains("%26count%3D100").contains("count=8");
    }
    @Test void weatherByCityContainsCurrentForecastAndLocalTimes() throws Exception {
        mvc.perform(get("/weather").param("city", "Москва").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.location.name").value("Москва"))
                .andExpect(jsonPath("$.timezone").value("Europe/Moscow"))
                .andExpect(jsonPath("$.current.temperature").value(-2.5))
                .andExpect(jsonPath("$.current.time").value("2026-11-24T09:00"))
                .andExpect(jsonPath("$.forecast[0].sunrise").value("2026-11-24T08:22"))
                .andExpect(jsonPath("$.forecast.length()").value(2));
        assertThat(forecastQuery).contains("forecast_days=7").contains("wind_speed_unit=ms").contains("timezone=auto");
    }
    @Test void coordinatesSkipGeocoding() throws Exception {
        mvc.perform(get("/weather").param("latitude", "0").param("longitude", "0").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.location.latitude").value(0));
        assertThat(cityQuery).isNull();
    }
    @Test void rejectsMissingAmbiguousAndInvalidCoordinates() throws Exception {
        for (String latitude : List.of("NaN", "Infinity", "91", "-91", "wrong"))
            mvc.perform(get("/weather").param("latitude", latitude).param("longitude", "0").header("Authorization", token()))
                    .andExpect(status().isBadRequest());
        mvc.perform(get("/weather").header("Authorization", token())).andExpect(status().isBadRequest());
        mvc.perform(get("/weather").param("latitude", "10").header("Authorization", token())).andExpect(status().isBadRequest());
        mvc.perform(get("/weather").param("city", "Москва").param("latitude", "10").header("Authorization", token()))
                .andExpect(status().isBadRequest());
        assertThat(forecastQuery).isNull();
    }
    @Test void unknownCityIs404AndEmptySearchIsList() throws Exception {
        locations = "{}";
        mvc.perform(get("/weather/locations").param("city", "Unknown").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/weather").param("city", "Unknown").header("Authorization", token())).andExpect(status().isNotFound());
    }
    @Test void upstreamErrorsAndIncompleteDataAre503() throws Exception {
        upstreamStatus = 429;
        mvc.perform(get("/weather").param("city", "Москва").header("Authorization", token()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").exists());
        upstreamStatus = 200; forecast = "{\"timezone\":\"UTC\"}";
        mvc.perform(get("/weather").param("latitude", "0").param("longitude", "0").header("Authorization", token()))
                .andExpect(status().isServiceUnavailable());
        forecast = "invalid-json";
        mvc.perform(get("/weather").param("latitude", "0").param("longitude", "0").header("Authorization", token()))
                .andExpect(status().isServiceUnavailable());
    }
}
