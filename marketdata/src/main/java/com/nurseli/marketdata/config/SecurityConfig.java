package com.nurseli.marketdata.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakJwtGrantedAuthoritiesConverter());
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/health/**",
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/ws/market"
                        ).permitAll()
                        // Piyasa ve haber okuma endpoint'leri frontend için public read-mode.
                        .requestMatchers(HttpMethod.GET,
                                "/api/news/**",
                                "/api/market/**",
                                "/api/funds/**",
                                "/api/fund/**",
                                "/api/viop/**",
                                "/api/debt/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/market/backfill/bist-daily").permitAll()
                        .requestMatchers(HttpMethod.POST, "/internal/market/backfill/isyatirim-metals-usd").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/**").hasAnyRole("ADMIN", "OPS")
                        .requestMatchers(req -> "OPTIONS".equalsIgnoreCase(req.getMethod())).permitAll()
                        .requestMatchers("/internal/**").hasAnyRole("ADMIN", "OPS")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }
}
