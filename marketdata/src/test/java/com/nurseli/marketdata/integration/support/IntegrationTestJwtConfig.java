package com.nurseli.marketdata.integration.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Integration testlerde Keycloak issuer/JWK çağrısı yapılmadan JWT decoder sağlar.
 */
@TestConfiguration
public class IntegrationTestJwtConfig {

    @Bean
    @Primary
    JwtDecoder integrationTestJwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .claim("sub", "unused")
                .build();
    }
}
