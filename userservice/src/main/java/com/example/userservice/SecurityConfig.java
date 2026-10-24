package com.example.userservice;

import com.example.sharedlib.security.ApiSecurity;
import com.example.sharedlib.security.JwtSecurityConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Import(JwtSecurityConfiguration.class)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        ApiSecurity.configure(http).authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/users/by-telegram/{telegramId}").authenticated()
                .requestMatchers("/users/{id}", "/users/{id}/settings", "/users/{id}/settings/**")
                .access((authentication, context) -> new AuthorizationDecision(
                        authentication.get().isAuthenticated()
                                && authentication.get().getName().equals(context.getVariables().get("id"))))
                // Registration replaces the legacy POST /users.
                .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
