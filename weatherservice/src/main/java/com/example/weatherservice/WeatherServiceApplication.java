package com.example.weatherservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.context.annotation.Import(com.example.sharedlib.redis.RedisStore.Config.class)
@SpringBootApplication
public class WeatherServiceApplication {
    public static void main(String[] args) { SpringApplication.run(WeatherServiceApplication.class, args); }
}
