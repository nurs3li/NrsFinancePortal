package com.nurseli.nrsfinanceportal.integration.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nurseli.nrsfinanceportal.config.KeycloakAdminProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KeycloakRealmSecurityClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final KeycloakAdminProperties adminProperties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final WebClient keycloakAdminWebClient;
    private final ObjectMapper objectMapper;

    public RealmSecuritySnapshot getRealmSecuritySnapshot() {
        String json = doGet("/admin/realms/" + adminProperties.getRealm());
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!(node instanceof ObjectNode rawNode)) {
                throw new IllegalStateException("Keycloak realm yaniti object degil");
            }
            return new RealmSecuritySnapshot(
                    node.path("rememberMe").asBoolean(false),
                    node.path("ssoSessionIdleTimeout").asInt(0),
                    node.path("ssoSessionMaxLifespan").asInt(0),
                    node.path("accessTokenLifespan").asInt(0),
                    node.path("clientSessionIdleTimeout").asInt(0),
                    node.path("clientSessionMaxLifespan").asInt(0),
                    rawNode
            );
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak realm security snapshot parse edilemedi", e);
        }
    }

    public void updateRealmSecurity(RealmSecurityUpdate update) {
        RealmSecuritySnapshot snapshot = getRealmSecuritySnapshot();
        ObjectNode merged = (ObjectNode) snapshot.raw().deepCopy();
        merged.put("rememberMe", update.rememberMe());
        merged.put("ssoSessionIdleTimeout", update.ssoSessionIdleTimeoutSec());
        merged.put("ssoSessionMaxLifespan", update.ssoSessionMaxLifespanSec());
        merged.put("accessTokenLifespan", update.accessTokenLifespanSec());
        merged.put("clientSessionIdleTimeout", update.clientSessionIdleTimeoutSec());
        merged.put("clientSessionMaxLifespan", update.clientSessionMaxLifespanSec());
        doPut("/admin/realms/" + adminProperties.getRealm(), merged.toString());
    }

    public List<UserLite> listUsersByRealmRole(String roleName) {
        List<UserLite> out = new ArrayList<>();
        int first = 0;
        int max = 200;
        while (true) {
            String json = doGet("/admin/realms/" + adminProperties.getRealm() + "/roles/" + roleName + "/users?first=" + first + "&max=" + max);
            try {
                JsonNode root = objectMapper.readTree(json);
                if (!root.isArray() || root.isEmpty()) {
                    break;
                }
                int pageCount = 0;
                for (JsonNode n : root) {
                    pageCount++;
                    out.add(new UserLite(
                            n.path("id").asText(null),
                            n.path("username").asText(null),
                            n
                    ));
                }
                if (pageCount < max) {
                    break;
                }
                first += max;
            } catch (Exception e) {
                throw new IllegalStateException("Keycloak role users parse edilemedi: " + roleName, e);
            }
        }
        return out;
    }

    public List<String> getRequiredActions(String userId) {
        String json = doGet("/admin/realms/" + adminProperties.getRealm() + "/users/" + userId);
        try {
            JsonNode node = objectMapper.readTree(json);
            ArrayNode arr = node.has("requiredActions") && node.get("requiredActions").isArray()
                    ? (ArrayNode) node.get("requiredActions")
                    : objectMapper.createArrayNode();
            List<String> actions = new ArrayList<>();
            Iterator<JsonNode> it = arr.iterator();
            while (it.hasNext()) {
                actions.add(it.next().asText(""));
            }
            return actions;
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak requiredActions parse edilemedi", e);
        }
    }

    public void addRequiredActionIfMissing(String userId, String requiredAction) {
        String json = doGet("/admin/realms/" + adminProperties.getRealm() + "/users/" + userId);
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(json);
            ArrayNode arr;
            if (node.has("requiredActions") && node.get("requiredActions").isArray()) {
                arr = (ArrayNode) node.get("requiredActions");
            } else {
                arr = objectMapper.createArrayNode();
            }
            boolean exists = false;
            for (JsonNode a : arr) {
                if (requiredAction.equalsIgnoreCase(a.asText(""))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                arr.add(requiredAction);
                ObjectNode update = objectMapper.createObjectNode();
                update.set("requiredActions", arr);
                doPut("/admin/realms/" + adminProperties.getRealm() + "/users/" + userId, update.toString());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak requiredAction update edilemedi", e);
        }
    }

    public void removeRequiredActionIfPresent(String userId, String requiredAction) {
        String json = doGet("/admin/realms/" + adminProperties.getRealm() + "/users/" + userId);
        try {
            ObjectNode node = (ObjectNode) objectMapper.readTree(json);
            ArrayNode existing = node.has("requiredActions") && node.get("requiredActions").isArray()
                    ? (ArrayNode) node.get("requiredActions")
                    : objectMapper.createArrayNode();
            ArrayNode filtered = objectMapper.createArrayNode();
            boolean changed = false;
            for (JsonNode action : existing) {
                if (requiredAction.equalsIgnoreCase(action.asText(""))) {
                    changed = true;
                    continue;
                }
                filtered.add(action.asText(""));
            }
            if (changed) {
                ObjectNode update = objectMapper.createObjectNode();
                update.set("requiredActions", filtered);
                doPut("/admin/realms/" + adminProperties.getRealm() + "/users/" + userId, update.toString());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak requiredAction remove edilemedi", e);
        }
    }

    public boolean hasOtpCredential(String userId) {
        String json = doGet("/admin/realms/" + adminProperties.getRealm() + "/users/" + userId + "/credentials");
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return false;
            }
            for (JsonNode cred : root) {
                String type = cred.path("type").asText("");
                if ("otp".equalsIgnoreCase(type)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak credential list parse edilemedi", e);
        }
    }

    public boolean ensureRequiredActionDefaultEnabled(String alias) {
        String json = doGet("/admin/realms/" + adminProperties.getRealm() + "/authentication/required-actions");
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) return false;
            for (JsonNode node : root) {
                String currentAlias = node.path("alias").asText("");
                if (!alias.equalsIgnoreCase(currentAlias)) continue;
                if (!(node instanceof ObjectNode actionNode)) continue;

                boolean enabled = actionNode.path("enabled").asBoolean(false);
                boolean defaultAction = actionNode.path("defaultAction").asBoolean(false);
                if (enabled && defaultAction) {
                    return false;
                }
                ObjectNode update = actionNode.deepCopy();
                update.put("enabled", true);
                update.put("defaultAction", true);
                doPut("/admin/realms/" + adminProperties.getRealm() + "/authentication/required-actions/" + currentAlias, update.toString());
                return true;
            }
            return false;
        } catch (Exception e) {
            throw new IllegalStateException("Keycloak required-action default ayari güncellenemedi", e);
        }
    }

    private String doGet(String path) {
        String base = KeycloakAdminTokenProvider.normalizeBase(adminProperties.getServerUrl());
        return keycloakAdminWebClient
                .get()
                .uri(base + path)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak GET " + path + " " + response.statusCode() + ": " + msg))))
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();
    }

    private void doPut(String path, String body) {
        String base = KeycloakAdminTokenProvider.normalizeBase(adminProperties.getServerUrl());
        keycloakAdminWebClient
                .put()
                .uri(base + path)
                .headers(h -> h.setBearerAuth(tokenProvider.getBearerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(msg -> Mono.error(new IllegalStateException(
                                        "Keycloak PUT " + path + " " + response.statusCode() + ": " + msg))))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }

    public record UserLite(String id, String username, JsonNode raw) {}

    public record RealmSecuritySnapshot(
            boolean rememberMe,
            int ssoSessionIdleTimeoutSec,
            int ssoSessionMaxLifespanSec,
            int accessTokenLifespanSec,
            int clientSessionIdleTimeoutSec,
            int clientSessionMaxLifespanSec,
            ObjectNode raw
    ) {}

    public record RealmSecurityUpdate(
            boolean rememberMe,
            int ssoSessionIdleTimeoutSec,
            int ssoSessionMaxLifespanSec,
            int accessTokenLifespanSec,
            int clientSessionIdleTimeoutSec,
            int clientSessionMaxLifespanSec
    ) {}
}
