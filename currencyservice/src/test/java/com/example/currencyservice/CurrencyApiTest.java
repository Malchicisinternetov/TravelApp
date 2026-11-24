package com.example.currencyservice;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "security.jwt.secret=test-only-signing-key-at-least-32-bytes-long")
@AutoConfigureMockMvc
class CurrencyApiTest {
    static final HttpServer provider;
    static volatile int upstreamStatus = 200;
    static volatile String rates;
    static final AtomicInteger calls = new AtomicInteger();
    static {
        try {
            provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            provider.createContext("/v2/", exchange -> {
                calls.incrementAndGet();
                String body = exchange.getRequestURI().getPath().endsWith("currencies") ? """
                        [{"iso_code":"USD","name":"US Dollar","type":"currency"},
                        {"iso_code":"JPY","name":"Yen","type":"currency"},
                        {"iso_code":"KWD","name":"Kuwaiti Dinar","type":"currency"},
                        {"iso_code":"BTC","name":"Bitcoin","type":"crypto"}]
                        """ : rates;
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(upstreamStatus, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            provider.start();
        } catch (Exception ex) { throw new ExceptionInInitializerError(ex); }
    }
    @DynamicPropertySource static void url(DynamicPropertyRegistry registry) {
        registry.add("currency.api-url", () -> "http://127.0.0.1:" + provider.getAddress().getPort());
    }
    @AfterAll static void stop() { provider.stop(0); }
    @Autowired MockMvc mvc;
    @Autowired SecretKey jwtSigningKey;
    @Autowired CurrencyService service;
    @BeforeEach void fixtures() {
        upstreamStatus = 200; calls.set(0);
        rates = """
                [{"date":"2026-11-23","base":"USD","quote":"JPY","rate":150.555},
                {"date":"2026-11-24","base":"USD","quote":"KWD","rate":0.30755}]
                """;
        ReflectionTestUtils.setField(service, "currencies", null);
        ((Map<?, ?>) ReflectionTestUtils.getField(service, "cache")).clear();
    }
    String token() {
        var claims = JwtClaimsSet.builder().subject("1").issuer("travelapp").audience(List.of("travelapp-api"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer " + new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey)).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    @Test void requiresJwtForEveryEndpoint() throws Exception {
        for (String path : List.of("/currencies", "/currencies/rates", "/currencies/convert"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        assertThat(calls.get()).isZero();
    }
    @Test void catalogueFiltersCryptoAndRatesKeepIndividualDatesAndAreCached() throws Exception {
        mvc.perform(get("/currencies").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        for (int i = 0; i < 2; i++) mvc.perform(get("/currencies/rates").param("base", "usd").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.base").value("USD"))
                .andExpect(jsonPath("$.rates[0].date").value("2026-11-23"));
        assertThat(calls.get()).isEqualTo(2);
    }
    @Test void convertsWithDecimalArithmeticAndCurrencySpecificRounding() throws Exception {
        mvc.perform(get("/currencies/convert").param("from", "usd").param("to", "JPY").param("amount", "10.1").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.convertedAmount").value("1521"))
                .andExpect(jsonPath("$.rate").value("150.555")).andExpect(jsonPath("$.date").value("2026-11-23"));
        mvc.perform(get("/currencies/convert").param("from", "USD").param("to", "KWD").param("amount", "10.1").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.convertedAmount").value("3.106"));
    }
    @Test void sameCurrencyNeedsNoRateAndZeroIsAllowed() throws Exception {
        mvc.perform(get("/currencies/convert").param("from", "USD").param("to", "USD").param("amount", "0").header("Authorization", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.convertedAmount").value("0.00"))
                .andExpect(jsonPath("$.rate").value("1")).andExpect(jsonPath("$.date").isEmpty());
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void validatesAmountsAndCodes() throws Exception {
        for (String amount : List.of("-1", "0.1234567", "1000000000000", "1e2147483647", "NaN"))
            mvc.perform(get("/currencies/convert").param("from", "USD").param("to", "JPY").param("amount", amount).header("Authorization", token()))
                    .andExpect(status().isBadRequest());
        mvc.perform(get("/currencies/rates").param("base", "../bad").header("Authorization", token())).andExpect(status().isBadRequest());
        mvc.perform(get("/currencies/rates").param("base", "BTC").header("Authorization", token())).andExpect(status().isBadRequest());
    }
    @Test void missingPairIs404() throws Exception {
        rates = "[{\"date\":\"2026-11-23\",\"base\":\"USD\",\"quote\":\"JPY\",\"rate\":150}]";
        mvc.perform(get("/currencies/convert").param("from", "USD").param("to", "KWD").param("amount", "1").header("Authorization", token()))
                .andExpect(status().isNotFound());
    }
    @Test void upstreamErrorsAndMalformedRatesAre503() throws Exception {
        upstreamStatus = 500;
        mvc.perform(get("/currencies").header("Authorization", token())).andExpect(status().isServiceUnavailable());
        upstreamStatus = 200; rates = "[{\"base\":\"USD\",\"quote\":\"JPY\",\"rate\":-1}]";
        mvc.perform(get("/currencies/rates").param("base", "USD").header("Authorization", token())).andExpect(status().isServiceUnavailable());
        rates = "invalid-json";
        mvc.perform(get("/currencies/rates").param("base", "USD").header("Authorization", token())).andExpect(status().isServiceUnavailable());
    }
}
