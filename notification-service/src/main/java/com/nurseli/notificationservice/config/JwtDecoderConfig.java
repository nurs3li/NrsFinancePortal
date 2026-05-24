package com.nurseli.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Set;

/**
 * Docker profilinde JWT doğrulama: tarayıcı {@code localhost:8081} issuer'ı ile giriş yaptığı için
 * JWKS {@code host.docker.internal:8081} üzerinden alınır; çoklu issuer kabul edilir.
 */
@Configuration
@Profile("docker")
public class JwtDecoderConfig {

    private static final String JWKS_URI = "http://host.docker.internal:8081/realms/nrs-finance/protocol/openid-connect/certs";

    /**
     * {@code jwtDecoder} — JWKS tabanlı decoder; varsayılan claim doğrulamasına ek olarak issuer whitelist uygular.
     */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> {
            String iss = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "";
            Set<String> allowedIssuers = Set.of(
                    "http://localhost:8081/realms/nrs-finance",
                    "http://nrs-keycloak:8080/realms/nrs-finance"
            );
            if (allowedIssuers.contains(iss)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Unexpected issuer: " + iss, null)
            );
        };
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                issuerValidator
        ));
        return decoder;
    }
}