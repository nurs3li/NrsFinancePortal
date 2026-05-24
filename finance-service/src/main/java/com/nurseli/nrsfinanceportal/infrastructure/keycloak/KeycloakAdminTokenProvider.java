package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.config.KeycloakAdminProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Keycloak admin REST API için client_credentials access token sağlar.
 */
/**
 * Keycloak confidential client ile client_credentials access token (cache'li).
 */
@Component
@RequiredArgsConstructor
public class KeycloakAdminTokenProvider {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final KeycloakAdminProperties properties;
    private final WebClient keycloakAdminWebClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    /**
     * Admin client_credentials yapılandırması tam mı kontrol eder.
     */
    public boolean isConfigured() {
        return properties.isEnabled()
                && properties.getRealm() != null && !properties.getRealm().isBlank()
                && properties.getClientId() != null && !properties.getClientId().isBlank()
                && properties.getClientSecret() != null && !properties.getClientSecret().isBlank();
    }

    /**
     * Yapılandırma eksikse IllegalStateException fırlatır.
     */
    public void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "Keycloak admin yapılandırması eksik. app.keycloak.admin.enabled=true ve client kimlik bilgilerini ayarlayın.");
        }
    }

    public static String normalizeBase(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalStateException("app.keycloak.admin.server-url boş.");
        }
        return serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
    }

    /**
     * Önbellekli admin access token döner (client_credentials).
     */
    public String getBearerToken() {
        requireConfigured();
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(10))) {
            return cachedAccessToken;
        }

        synchronized (this) {
            if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(10))) {
                return cachedAccessToken;
            }

            String base = normalizeBase(properties.getServerUrl());
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.getClientId());
            form.add("client_secret", properties.getClientSecret());

            String raw = keycloakAdminWebClient
                    .post()
                    .uri(base + "/realms/" + properties.getRealm() + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .onStatus(s -> !s.is2xxSuccessful(), response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(msg -> Mono.error(new IllegalStateException(
                                            "Keycloak token " + response.statusCode() + ": " + msg))))
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();

            try {
                JsonNode tree = raw != null ? objectMapper.readTree(raw) : null;
                if (tree == null || !tree.has("access_token")) {
                    throw new IllegalStateException("Keycloak token yanıtı geçersiz");
                }
                cachedAccessToken = tree.get("access_token").asText();
                long expSec = tree.has("expires_in") ? tree.get("expires_in").asLong(60L) : 60L;
                tokenExpiresAt = Instant.now().plusSeconds(Math.max(expSec, 60L));
                return cachedAccessToken;
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Keycloak access token alınamadı", e);
            }
        }
    }
}
