package com.nurseli.notificationservice.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Keycloak client credentials akışı ile servisler arası (S2S) access token alır;
 * finance-service internal çağrılarında Authorization header için kullanılır.
 */
@Slf4j
@Component
public class S2SAccessTokenService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private final WebClient webClient;

    public S2SAccessTokenService() {
        this(WebClient.builder().build());
    }

    /**
     * {@code S2SAccessTokenService} — Test ve özel {@link WebClient} enjeksiyonu için paket görünümlü kurucu.
     */
    S2SAccessTokenService(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * {@code getAccessToken} — Keycloak token endpoint'inden geçerli S2S access token döner;
     * yanıt boş veya hatalıysa istisna fırlatır.
     */
    public String getAccessToken() {
        String tokenUrl = authServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        try {
            TokenResponse response = webClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (response == null || response.access_token == null || response.access_token.isBlank()) {
                log.error("[S2S] Empty token response from Keycloak");
                throw new IllegalStateException("Failed to obtain S2S access token");
            }

            return response.access_token;
        } catch (Exception e) {
            log.error("[S2S] Failed to obtain access token from Keycloak", e);
            throw new IllegalStateException("Failed to obtain S2S access token", e);
        }
    }

    /** Keycloak OAuth2 token endpoint yanıt eşlemesi. */
    public record TokenResponse(
            String access_token,
            String token_type,
            Long expires_in,
            String scope
    ) {}
}