package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.config.KeycloakAdminProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Keycloak kullanıcı etkinleştirme/devre dışı bırakma.
 */
/**
 * Keycloak kullanıcısını JWT sub (UUID) üzerinden etkin/devre dışı bırakır.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserEnablementClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;
    private final ObjectMapper objectMapper;

    /**
     * Client yapılandırması hazır mı döner.
     */
    public boolean isConfigured() {
        return tokenProvider.isConfigured();
    }

    /**
     * Keycloak kullanıcı enabled bayrağını günceller.
     */
    public void setEnabled(String keycloakUserId, boolean enabled) {
        tokenProvider.requireConfigured();

        JsonNode representation = fetchUserRepresentation(keycloakUserId);

        final String body;
        try {
            com.fasterxml.jackson.databind.node.ObjectNode node =
                    (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(representation.toString());
            node.put("enabled", enabled);
            body = objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak kullanıcı temsili güncellenemedi", e);
        }

        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String uri = "/admin/realms/" + properties.getRealm() + "/users/" + keycloakUserId;

        keycloakAdminWebClient
                .put()
                .uri(base + uri)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak PUT kullanıcı " + response.statusCode() + ": " + msg))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();

        log.info("[KEYCLOAK] user {} enabled={}", keycloakUserId, enabled);
    }

    private JsonNode fetchUserRepresentation(String keycloakUserId) {
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String uri = "/admin/realms/" + properties.getRealm() + "/users/" + keycloakUserId;

        String json = keycloakAdminWebClient
                .get()
                .uri(base + uri)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response -> {
                    if (response.statusCode().value() == 404) {
                        return Mono.error(new IllegalStateException("Keycloak'ta kullanıcı bulunamadı"));
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(msg -> Mono.error(new IllegalStateException(
                                    "Keycloak GET kullanıcı " + response.statusCode() + ": " + msg)));
                })
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        try {
            return json != null ? objectMapper.readTree(json) : null;
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak kullanıcı bilgisi okunamadı", e);
        }
    }
}
