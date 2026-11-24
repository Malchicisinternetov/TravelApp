package com.example.currencyservice;

import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/currencies")
public class CurrencyController {
    private final CurrencyService service;
    public CurrencyController(CurrencyService service) { this.service = service; }
    @GetMapping
    public List<CurrencyService.CurrencyInfo> currencies() { return service.currencies(); }
    @GetMapping("/rates")
    public CurrencyService.Rates rates(@RequestParam(defaultValue = "RUB") String base) { return service.rates(base); }
    @GetMapping("/convert")
    public CurrencyService.Conversion convert(@RequestParam String from, @RequestParam String to, @RequestParam BigDecimal amount) {
        return service.convert(from, to, amount);
    }
}
