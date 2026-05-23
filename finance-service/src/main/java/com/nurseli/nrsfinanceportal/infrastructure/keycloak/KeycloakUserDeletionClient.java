package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

import com.nurseli.nrsfinanceportal.config.KeycloakAdminProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Keycloak Admin API ile kullanıcı silme.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserDeletionClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;

    /**
     * Client yapılandırması hazır mı döner.
     */
    public boolean isConfigured() {
        return tokenProvider.isConfigured();
    }

    /**
     * Keycloak'tan kullanıcıyı kalıcı siler.
     */
    public void deleteUser(String keycloakUserId) {
        tokenProvider.requireConfigured();
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String path = "/admin/realms/" + properties.getRealm() + "/users/" + keycloakUserId;

        keycloakAdminWebClient
                .delete()
                .uri(base + path)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response -> {
                    if (response.statusCode().value() == 404) {
                        log.warn("[KEYCLOAK] delete user already absent id={}", keycloakUserId);
                        return Mono.empty();
                    }
                    return response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(msg -> Mono.error(new IllegalStateException(
                                    "Keycloak kullanıcı silme " + response.statusCode() + ": " + msg)));
                })
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();

        log.info("[KEYCLOAK] user deleted id={}", keycloakUserId);
    }
}
