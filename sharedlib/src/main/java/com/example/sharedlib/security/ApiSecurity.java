package com.example.sharedlib.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.config.Customizer;

public final class ApiSecurity {
    private ApiSecurity() {}

    public static final AuthenticationEntryPoint UNAUTHORIZED = (request, response, exception) -> {
        response.setStatus(401);
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Authentication required or invalid token\",\"status\":401}");
    };

    public static final AccessDeniedHandler FORBIDDEN = (request, response, exception) -> {
        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Access denied\",\"status\":403}");
    };

    public static HttpSecurity configure(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(UNAUTHORIZED)
                        .accessDeniedHandler(FORBIDDEN))
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(UNAUTHORIZED).accessDeniedHandler(FORBIDDEN));
    }
}
