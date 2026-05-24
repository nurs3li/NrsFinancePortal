package com.nurseli.nrsfinanceportal.infrastructure.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nurseli.nrsfinanceportal.config.KeycloakAdminProperties;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Keycloak realm rol atama/kaldırma.
 */
/**
 * Realm rollerini (USER, ADMIN) Keycloak kullanıcısına map'ler.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakRealmRoleMappingClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Set<String> APP_REALM_ROLES = Set.of("USER", "ADMIN");

    private final KeycloakAdminProperties properties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedRealmInternalId;

    /**
     * Uygulama realm rollerinden hedef rolü bırakır; diğerlerini kaldırır (composite hariç doğrudan atamalar).
     */
    public void replaceApplicationRealmRole(String keycloakUserId, Role newRole) {
        tokenProvider.requireConfigured();
        String base = KeycloakAdminTokenProvider.normalizeBase(properties.getServerUrl());
        String realm = properties.getRealm();

        JsonNode toRemove = collectDirectAppRolesToRemove(base, realm, keycloakUserId);
        if (toRemove instanceof ArrayNode an && !an.isEmpty()) {
            deleteRealmRoleMappings(base, realm, keycloakUserId, an);
        }

        JsonNode newRoleRep = resolveRealmRoleRepresentation(base, realm, newRole.name());
        postRealmRoleMappings(base, realm, keycloakUserId, List.of(newRoleRep));
        log.info("[KEYCLOAK] user {} realm role set to {}", keycloakUserId, newRole.name());
    }

    private JsonNode collectDirectAppRolesToRemove(String base, String realm, String keycloakUserId) {
        String json = keycloakAdminWebClient
                .get()
                .uri(base + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/role-mappings/realm")
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak GET role-mappings " + response.statusCode() + ": " + msg))))
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        ArrayNode out = objectMapper.createArrayNode();
        try {
            JsonNode arr = json != null ? objectMapper.readTree(json) : null;
            if (arr == null || !arr.isArray()) {
                return out;
            }
            for (JsonNode n : arr) {
                if (!n.has("name")) {
                    continue;
                }
                String name = n.get("name").asText();
                if (!APP_REALM_ROLES.contains(name)) {
                    continue;
                }
                if (n.has("composite") && n.get("composite").asBoolean()) {
                    continue;
                }
                out.add(n);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak rol eşlemesi okunamadı", e);
        }
    }

    private void deleteRealmRoleMappings(String base, String realm, String keycloakUserId, JsonNode roleArray) {
        String uri = base + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/role-mappings/realm";
        String body = roleArray.toString();
        keycloakAdminWebClient
                .method(HttpMethod.DELETE)
                .uri(uri)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak DELETE role-mappings " + response.statusCode() + ": " + msg))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }

    private void postRealmRoleMappings(String base, String realm, String keycloakUserId, List<JsonNode> roles) {
        ArrayNode body = objectMapper.createArrayNode();
        roles.forEach(body::add);
        String uri = base + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/role-mappings/realm";
        keycloakAdminWebClient
                .post()
                .uri(uri)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString())
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak POST role-mappings " + response.statusCode() + ": " + msg))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }

    /**
     * Keycloak bazı sürümlerde yalnızca "name" ile POST → 404 Role not found.
     * Önce tüm realm rollerini listeler (id dahil); olmazsa realm iç id + name ile gönderir.
     */
    private JsonNode resolveRealmRoleRepresentation(String base, String realm, String roleName) {
        String listJson = null;
        try {
            listJson = keycloakAdminWebClient
                    .get()
                    .uri(base + "/admin/realms/" + realm + "/roles")
                    .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                    .retrieve()
                    .onStatus(s -> !s.is2xxSuccessful(), response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(msg -> Mono.error(new IllegalStateException(
                                            "Keycloak GET roles list " + response.statusCode() + ": " + msg))))
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (RuntimeException e) {
            log.warn("[KEYCLOAK] realm rol listesi alınamadı ({}), alternatif denenecek", e.getMessage());
        }

        if (listJson != null) {
            try {
                JsonNode arr = objectMapper.readTree(listJson);
                if (arr.isArray()) {
                    for (JsonNode r : arr) {
                        if (r.has("name") && roleName.equals(r.get("name").asText())) {
                            return copyRealmRoleForUserMapping(r);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[KEYCLOAK] rol listesi parse edilemedi", e);
            }
        }

        String realmId = fetchRealmInternalId(base, realm);
        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.put("name", roleName);
        fallback.put("clientRole", false);
        fallback.put("containerId", realmId);
        return fallback;
    }

    private JsonNode copyRealmRoleForUserMapping(JsonNode source) {
        ObjectNode out = objectMapper.createObjectNode();
        if (source.has("id")) {
            out.set("id", source.get("id"));
        }
        if (source.has("name")) {
            out.set("name", source.get("name"));
        }
        out.put("clientRole", false);
        if (source.has("composite")) {
            out.set("composite", source.get("composite"));
        }
        if (source.has("containerId")) {
            out.set("containerId", source.get("containerId"));
        }
        return out;
    }

    private String fetchRealmInternalId(String base, String realm) {
        if (cachedRealmInternalId != null) {
            return cachedRealmInternalId;
        }
        synchronized (this) {
            if (cachedRealmInternalId != null) {
                return cachedRealmInternalId;
            }
            String json = keycloakAdminWebClient
                    .get()
                    .uri(base + "/admin/realms/" + realm)
                    .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                    .retrieve()
                    .onStatus(s -> !s.is2xxSuccessful(), response ->
                            response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(msg -> Mono.error(new IllegalStateException(
                                            "Keycloak GET realm " + response.statusCode() + ": " + msg
                                                    + " — service account'a realm-management → view-realm atayın."))))
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();
            try {
                JsonNode tree = json != null ? objectMapper.readTree(json) : null;
                if (tree == null || !tree.has("id")) {
                    throw new IllegalStateException("Keycloak realm yanıtında id yok");
                }
                cachedRealmInternalId = tree.get("id").asText();
                return Objects.requireNonNull(cachedRealmInternalId);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Keycloak realm id okunamadı", e);
            }
        }
    }
}
