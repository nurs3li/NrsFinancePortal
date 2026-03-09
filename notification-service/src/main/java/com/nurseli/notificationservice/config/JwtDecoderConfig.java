package com.nurseli.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@Profile("docker")
public class JwtDecoderConfig {

    /**
     * Docker'da: Tarayıcı localhost:8081 ile giriş yaptığı için token'daki iss
     * http://localhost:8081/realms/nrs-finance olur. JWKS container'dan
     * host.docker.internal:8081 ile alınır.
     */
    private static final String JWKS_URI = "http://host.docker.internal:8081/realms/nrs-finance/protocol/openid-connect/certs";
    private static final String EXPECTED_ISSUER = "http://localhost:8081/realms/nrs-finance";

    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(EXPECTED_ISSUER));
        return decoder;
    }
}