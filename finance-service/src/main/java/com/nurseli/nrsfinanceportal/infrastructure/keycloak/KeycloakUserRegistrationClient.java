package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nurseli.nrsfinanceportal.config.KeycloakAdminProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Keycloak Admin API ile kullanıcı kaydı oluşturur.
 */
@Component
@RequiredArgsConstructor
public class KeycloakUserRegistrationClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;
    private final ObjectMapper objectMapper;

    /**
     * Keycloak'ta yeni kullanıcı oluşturur; oluşan keycloak user id döner.
     */
    public String createUser(
            String username,
            String email,
            String firstName,
            String lastName,
            String password,
            boolean emailVerified,
            List<String> requiredActions) {
        tokenProvider.requireConfigured();
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String realm = properties.getRealm();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("username", username);
        payload.put("email", email);
        payload.put("firstName", firstName);
        payload.put("lastName", lastName);
        payload.put("enabled", true);
        payload.put("emailVerified", emailVerified);

        if (requiredActions != null && !requiredActions.isEmpty()) {
            ArrayNode arr = objectMapper.createArrayNode();
            requiredActions.forEach(arr::add);
            payload.set("requiredActions", arr);
        }

        String location = keycloakAdminWebClient
                .post()
                .uri(base + "/admin/realms/" + realm + "/users")
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload.toString())
                .exchangeToMono(resp -> {
                    if (resp.statusCode().is2xxSuccessful()) {
                        String loc = resp.headers().asHttpHeaders().getFirst("Location");
                        return Mono.just(loc != null ? loc : "");
                    }
                    return resp.bodyToMono(String.class).defaultIfEmpty("")
                            .flatMap(msg -> Mono.error(new IllegalStateException(
                                    "Keycloak kullanıcı oluşturma başarısız " + resp.statusCode() + ": " + msg)));
                })
                .timeout(TIMEOUT)
                .block();

        String userId = extractUserIdFromLocation(location);
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("Keycloak user id Location header'dan alınamadı");
        }

        setPassword(userId, password);
        return userId;
    }

    private void setPassword(String userId, String rawPassword) {
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String realm = properties.getRealm();
        ObjectNode cred = objectMapper.createObjectNode();
        cred.put("type", "password");
        cred.put("temporary", false);
        cred.put("value", rawPassword);

        keycloakAdminWebClient
                .put()
                .uri(base + "/admin/realms/" + realm + "/users/" + userId + "/reset-password")
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cred.toString())
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak şifre set edilemedi " + response.statusCode() + ": " + msg))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }

    private String extractUserIdFromLocation(String location) {
        if (location == null || location.isBlank()) return null;
        int idx = location.lastIndexOf('/');
        if (idx < 0 || idx == location.length() - 1) return null;
        return location.substring(idx + 1);
    }
}
