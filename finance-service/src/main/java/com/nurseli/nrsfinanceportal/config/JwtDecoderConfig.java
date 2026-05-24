package com.nurseli.nrsfinanceportal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Set;

/**
 * Keycloak JWKS üzerinden JWT decoder bean.
 */
@Configuration
@Profile("docker")
public class JwtDecoderConfig {

    /**
     * Aynı realm imzaları; JWKS container ağından Keycloak'a.
     * iss: tarayıcı → http://localhost:8081/realms/...
     * iss: notification-service S2S (client_credentials) → http://nrs-keycloak:8080/realms/...
     */
    private static final String JWKS_URI = "http://nrs-keycloak:8080/realms/nrs-finance/protocol/openid-connect/certs";

    @Bean
    @Primary
    /**
 * Docker profilinde çoklu issuer destekli JWKS JwtDecoder bean.
 */
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        OAuth2TokenValidator<Jwt> issuerValidator = jwt -> {
            String iss = jwt.getIssuer() != null ? jwt.getIssuer().toString() : "";
            Set<String> allowed = Set.of(
                    "http://localhost:8081/realms/nrs-finance",
                    "http://nrs-keycloak:8080/realms/nrs-finance"
            );
            if (allowed.contains(iss)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Unexpected issuer: " + iss, null)
            );
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                issuerValidator
        ));
        return decoder;
    }
}