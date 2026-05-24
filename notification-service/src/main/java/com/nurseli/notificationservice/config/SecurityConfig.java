package com.nurseli.notificationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.nurseli.notificationservice.config.ApiPaths;

/**
 * OAuth2 Resource Server JWT güvenlik zinciri: public actuator/swagger, korumalı {@code /api/notifications/**}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiSecurityErrorHandler apiSecurityErrorHandler;

    /**
     * {@code filterChain} — CORS, CSRF devre dışı, endpoint yetkilendirme ve JWT resource server yapılandırması.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .requestMatchers(req -> "OPTIONS".equalsIgnoreCase(req.getMethod())).permitAll()
                        .requestMatchers(
                                ApiPaths.v1FromLegacy("/api/notifications/**"),
                                "/api/notifications/**"
                        ).authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiSecurityErrorHandler)
                        .accessDeniedHandler(apiSecurityErrorHandler))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}