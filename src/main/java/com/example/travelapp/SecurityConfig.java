package com.example.travelapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // Разрешить все запросы без логина
                )
                .csrf(csrf -> csrf.disable()) // Отключаем CSRF, чтобы работали POST/PUT запросы
                .formLogin(form -> form.disable()) // Отключаем форму логина
                .httpBasic(basic -> basic.disable()); // Отключаем Basic-аутентификацию

        return http.build();
    }
}
