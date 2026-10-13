package com.example.userservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
                .csrf(AbstractHttpConfigurer::disable) // Отключаем CSRF, чтобы работали POST/PUT запросы
                .formLogin(AbstractHttpConfigurer::disable) // Отключаем форму логина
                .httpBasic(AbstractHttpConfigurer::disable); // Отключаем Basic-аутентификацию

        return http.build();
    }
}
