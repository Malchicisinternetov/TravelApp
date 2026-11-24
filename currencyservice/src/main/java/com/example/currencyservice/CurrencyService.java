package com.example.currencyservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.example.sharedlib.redis.RedisStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class CurrencyService {
    public record CurrencyInfo(String code, String name) {}
    record RawCurrency(@JsonProperty("iso_code") String code, String name, String type) {}
    public record Rate(LocalDate date, String base, String quote, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal rate) {}
    public record Rates(String base, Instant fetchedAt, String provider, List<Rate> rates) {}
    public record Conversion(@JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount, String from, String to,
                             @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal convertedAmount,
                             @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal rate, LocalDate date, Instant fetchedAt, String provider) {}

    private final RestClient client;
    private final RedisStore redis;

    public CurrencyService(@Value("${currency.api-url}") String url, RedisStore redis) {
        this.redis = redis;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public List<CurrencyInfo> currencies() {
        return Arrays.asList(redis.cached("currency:catalogue", Duration.ofDays(1), CurrencyInfo[].class,
                () -> loadCurrencies().toArray(CurrencyInfo[]::new)));
    }

    private List<CurrencyInfo> loadCurrencies() {
        var raw = client.get().uri("/v2/currencies").retrieve().body(RawCurrency[].class);
        if (raw == null || raw.length == 0) throw unavailable();
        var currencies = Arrays.stream(raw).filter(c -> c.code() != null && c.code().matches("[A-Z]{3}")
                        && "currency".equals(c.type()) && supported(c.code()))
                .map(c -> new CurrencyInfo(c.code(), c.name())).sorted(Comparator.comparing(CurrencyInfo::code)).toList();
        if (currencies.isEmpty()) throw unavailable();
        return currencies;
    }

    private static boolean supported(String code) {
        try { return Currency.getInstance(code).getDefaultFractionDigits() >= 0; }
        catch (IllegalArgumentException ex) { return false; }
    }

    private String code(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) throw new ResponseStatusException(BAD_REQUEST, "Укажите трёхбуквенный код валюты, например RUB.");
        if (currencies().stream().noneMatch(c -> c.code().equals(code)))
            throw new ResponseStatusException(BAD_REQUEST, "Валюта не поддерживается: " + code);
        return code;
    }

    public Rates rates(String base) {
        String normalized = code(base);
        return redis.cached("currency:rates:" + normalized, Duration.ofHours(1), Rates.class, () -> loadRates(normalized));
    }

    private Rates loadRates(String base) {
        var raw = client.get().uri("/v2/rates?base={base}", base).retrieve().body(Rate[].class);
        if (raw == null || raw.length == 0) throw unavailable();
        Set<String> supported = new HashSet<>();
        currencies().forEach(c -> supported.add(c.code()));
        List<Rate> result = new ArrayList<>();
        for (Rate rate : raw) {
            if (rate.base() == null || !base.equals(rate.base()) || rate.date() == null || rate.rate() == null || rate.rate().signum() <= 0)
                throw unavailable();
            if (supported.contains(rate.quote())) result.add(rate);
        }
        if (result.isEmpty()) throw unavailable();
        result.sort(Comparator.comparing(Rate::quote));
        Rates response = new Rates(base, Instant.now(), "Frankfurter", List.copyOf(result));
        return response;
    }

    public Conversion convert(String from, String to, BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.scale() > 6 || (long) amount.precision() - amount.scale() > 12)
            throw new ResponseStatusException(BAD_REQUEST, "Сумма должна быть неотрицательной: до 12 цифр и 6 знаков после запятой.");
        from = code(from);
        to = code(to);
        int scale = Currency.getInstance(to).getDefaultFractionDigits();
        if (from.equals(to)) return new Conversion(amount, from, to, amount.setScale(scale, RoundingMode.HALF_UP),
                BigDecimal.ONE, null, Instant.now(), "Frankfurter");
        var rates = rates(from);
        String target = to;
        var rate = rates.rates().stream().filter(r -> r.quote().equals(target)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Для этой пары валют курс пока недоступен."));
        return new Conversion(amount, from, to, amount.multiply(rate.rate()).setScale(scale, RoundingMode.HALF_UP),
                rate.rate(), rate.date(), rates.fetchedAt(), rates.provider());
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(SERVICE_UNAVAILABLE, "Сервис валют вернул неполные данные. Повторите позже.");
    }
}
