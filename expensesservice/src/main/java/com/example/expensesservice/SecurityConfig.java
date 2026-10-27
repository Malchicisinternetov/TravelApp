package com.example.expensesservice;

import com.example.sharedlib.security.ApiSecurity;
import com.example.sharedlib.security.JwtSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Import(JwtSecurityConfiguration.class)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ApiSecurity.configure(http).authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }
}
