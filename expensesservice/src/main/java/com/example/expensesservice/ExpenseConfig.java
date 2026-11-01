package com.example.expensesservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ExpenseConfig {
    @Bean
    public Clock expenseClock(@Value("${expenses.time-zone:Europe/Moscow}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
