package com.example.weatherservice;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/weather")
public class WeatherController {
    private final WeatherService service;
    public WeatherController(WeatherService service) { this.service = service; }

    @GetMapping("/locations")
    public List<WeatherService.Location> locations(@RequestParam String city) { return service.locations(city); }

    @GetMapping
    public WeatherService.Weather weather(@RequestParam(required = false) String city,
                                          @RequestParam(required = false) Double latitude,
                                          @RequestParam(required = false) Double longitude) {
        return service.weather(city, latitude, longitude);
    }
}
