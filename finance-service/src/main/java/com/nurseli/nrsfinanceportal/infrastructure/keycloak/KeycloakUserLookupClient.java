package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurseli.nrsfinanceportal.config.KeycloakAdminProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

/**
 * Keycloak kullanıcı arama ve kimlik çözümleme.
 */
@Component
@RequiredArgsConstructor
public class KeycloakUserLookupClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;
    private final ObjectMapper objectMapper;

    public record UserLite(String id, String username, String email) {}

    /**
     * Kullanıcı adına göre Keycloak user id arar.
     */
    public String findUserIdByUsername(String username) {
        UserLite user = findUser(username);
        return user != null ? user.id() : null;
    }

    /**
     * E-postaya göre Keycloak user id arar.
     */
    public String findUserIdByEmail(String email) {
        UserLite user = findUser(email);
        return user != null ? user.id() : null;
    }

    /**
     * Kullanıcı adı veya e-posta ile UserLite döner.
     */
    public UserLite findUser(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return null;
        }
        String trimmed = usernameOrEmail.trim();
        String field = trimmed.contains("@") ? "email" : "username";
        return findUserByField(field, trimmed);
    }

    /**
     * Giriş için canonical username çözümler.
     */
    public String resolveLoginUsername(String usernameOrEmail) {
        UserLite user = findUser(usernameOrEmail);
        if (user != null && user.username() != null && !user.username().isBlank()) {
            return user.username();
        }
        return usernameOrEmail == null ? "" : usernameOrEmail.trim();
    }

    private UserLite findUserByField(String field, String value) {
        tokenProvider.requireConfigured();
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        URI uri = UriComponentsBuilder.fromHttpUrl(base)
                .path("/admin/realms/{realm}/users")
                .queryParam(field, value)
                .buildAndExpand(properties.getRealm())
                .encode()
                .toUri();

        String json = keycloakAdminWebClient
                .get()
                .uri(uri)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak kullanıcı arama " + response.statusCode() + ": " + msg))))
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray() || root.isEmpty()) {
                return null;
            }
            JsonNode node = root.get(0);
            return new UserLite(
                    node.path("id").asText(null),
                    node.path("username").asText(null),
                    node.path("email").asText(null)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak kullanıcı arama yanıtı okunamadı", e);
        }
    }
}
