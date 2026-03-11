package com.nurseli.notificationservice.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class S2SAccessTokenService {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private final WebClient webClient = WebClient.builder().build();

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

    public record TokenResponse(
            String access_token,
            String token_type,
            Long expires_in,
            String scope
    ) {}
}