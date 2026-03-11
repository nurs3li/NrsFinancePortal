package com.nurseli.notificationservice.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailTokenService {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final GmailProperties gmailProperties;

    private final WebClient webClient = WebClient.builder().build();

    public String getAccessToken() {
        if (gmailProperties.getClientId() == null
                || gmailProperties.getClientSecret() == null
                || gmailProperties.getRefreshToken() == null) {
            log.error("[GMAIL] Missing OAuth2 configuration (clientId / clientSecret / refreshToken)");
            throw new IllegalStateException("Gmail OAuth2 configuration missing");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", gmailProperties.getClientId());
        form.add("client_secret", gmailProperties.getClientSecret());
        form.add("refresh_token", gmailProperties.getRefreshToken());
        form.add("grant_type", "refresh_token");

        GmailTokenResponse response = webClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(GmailTokenResponse.class)
                .block();

        if (response == null || response.access_token == null || response.access_token.isBlank()) {
            log.error("[GMAIL] Failed to obtain access token from Google");
            throw new IllegalStateException("Failed to obtain Gmail access token");
        }

        return response.access_token;
    }

    /**
     * Google token endpoint response mapping.
     * Sadece ihtiyacımız olan alanları ekliyoruz.
     */
    public record GmailTokenResponse(
            String access_token,
            String token_type,
            Long expires_in
    ) {}
}