package com.example.currencyservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.context.annotation.Import(com.example.sharedlib.redis.RedisStore.Config.class)
@SpringBootApplication
public class CurrencyServiceApplication {
    public static void main(String[] args) { SpringApplication.run(CurrencyServiceApplication.class, args); }
}
