package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.config.KeycloakSecurityProperties;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakAdminTokenProvider;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakRealmSecurityClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakSecurityEnforcementService {

    private final KeycloakSecurityProperties securityProperties;
    private final KeycloakAdminTokenProvider tokenProvider;
    private final KeycloakRealmSecurityClient realmSecurityClient;

    @EventListener(ApplicationReadyEvent.class)
    public void onAppReady() {
        runReconcile("startup");
    }

    @Scheduled(cron = "${app.keycloak.security.reconcile-cron:0 0 */6 * * *}")
    public void scheduledReconcile() {
        runReconcile("schedule");
    }

    public void runReconcile(String source) {
        boolean otp = securityProperties.isOtpEnforcementEnabled();
        boolean remember = securityProperties.isRememberMeEnforcementEnabled();
        if (!otp && !remember) {
            log.info("[KEYCLOAK_SECURITY][{}] skip reason=flags_disabled otpEnabled={} rememberEnabled={}", source, otp, remember);
            return;
        }
        if (!tokenProvider.isConfigured()) {
            log.warn("[KEYCLOAK_SECURITY][{}] skip reason=admin_client_not_configured", source);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            if (remember) {
                summary.put("realm", enforceRealmSettings());
            } else {
                summary.put("realm", "skipped");
            }
            if (otp) {
                summary.put("otp", enforceOtpRequiredAction());
            } else {
                summary.put("otp", "skipped");
            }
            long took = System.currentTimeMillis() - start;
            log.info("[KEYCLOAK_SECURITY][{}] completed tookMs={} summary={}", source, took, summary);
        } catch (Exception ex) {
            log.warn("[KEYCLOAK_SECURITY][{}] failed reason={}", source, ex.getMessage(), ex);
        }
    }

    private Map<String, Object> enforceRealmSettings() {
        var current = realmSecurityClient.getRealmSecuritySnapshot();
        int ssoIdleSec = (int) securityProperties.getSsoIdle().getSeconds();
        int ssoMaxSec = (int) securityProperties.getSsoMax().getSeconds();
        int accessSec = (int) securityProperties.getAccessTokenLifespan().getSeconds();
        int clientIdleSec = (int) securityProperties.getClientSessionIdle().getSeconds();
        int clientMaxSec = (int) securityProperties.getClientSessionMax().getSeconds();

        boolean drift =
                current.rememberMe() != securityProperties.isRememberMe()
                        || current.ssoSessionIdleTimeoutSec() != ssoIdleSec
                        || current.ssoSessionMaxLifespanSec() != ssoMaxSec
                        || current.accessTokenLifespanSec() != accessSec
                        || current.clientSessionIdleTimeoutSec() != clientIdleSec
                        || current.clientSessionMaxLifespanSec() != clientMaxSec;

        if (!drift) {
            return Map.of("changed", false, "reason", "already_in_sync");
        }

        realmSecurityClient.updateRealmSecurity(new KeycloakRealmSecurityClient.RealmSecurityUpdate(
                securityProperties.isRememberMe(),
                ssoIdleSec,
                ssoMaxSec,
                accessSec,
                clientIdleSec,
                clientMaxSec
        ));
        return Map.of(
                "changed", true,
                "rememberMe", securityProperties.isRememberMe(),
                "ssoIdleSec", ssoIdleSec,
                "ssoMaxSec", ssoMaxSec,
                "accessSec", accessSec,
                "clientIdleSec", clientIdleSec,
                "clientMaxSec", clientMaxSec
        );
    }

    private Map<String, Object> enforceOtpRequiredAction() {
        String requiredAction = normalizeRequiredAction(securityProperties.getRequiredAction());
        List<String> roles = securityProperties.getEnforcedRoles() == null ? List.of() : securityProperties.getEnforcedRoles();

        AtomicInteger scanned = new AtomicInteger();
        AtomicInteger changed = new AtomicInteger();
        boolean defaultActionChanged = false;
        try {
            defaultActionChanged = realmSecurityClient.ensureRequiredActionDefaultEnabled(requiredAction);
        } catch (Exception ex) {
            log.warn("[KEYCLOAK_SECURITY][otp] default_action_sync_failed action={} reason={}",
                    requiredAction, ex.getMessage());
        }

        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            List<KeycloakRealmSecurityClient.UserLite> users = realmSecurityClient.listUsersByRealmRole(role.trim());
            for (KeycloakRealmSecurityClient.UserLite user : users) {
                if (user.id() == null || user.id().isBlank()) {
                    continue;
                }
                scanned.incrementAndGet();
                boolean hasOtp;
                try {
                    hasOtp = realmSecurityClient.hasOtpCredential(user.id());
                } catch (Exception ex) {
                    log.warn("[KEYCLOAK_SECURITY][otp] otp_credential_check_failed user={} role={} reason={}",
                            user.username() != null ? user.username() : user.id(), role, ex.getMessage());
                    continue;
                }
                if (hasOtp) {
                    try {
                        realmSecurityClient.removeRequiredActionIfPresent(user.id(), requiredAction);
                    } catch (Exception ex) {
                        log.warn("[KEYCLOAK_SECURITY][otp] action_cleanup_failed user={} role={} action={} reason={}",
                                user.username() != null ? user.username() : user.id(), role, requiredAction, ex.getMessage());
                    }
                    continue;
                }
                List<String> currentActions = realmSecurityClient.getRequiredActions(user.id());
                boolean hasAction = currentActions.stream()
                        .anyMatch(a -> requiredAction.equalsIgnoreCase(a));
                if (!hasAction) {
                    realmSecurityClient.addRequiredActionIfMissing(user.id(), requiredAction);
                    changed.incrementAndGet();
                    log.info("[KEYCLOAK_SECURITY][otp] action_added user={} role={} action={}",
                            user.username() != null ? user.username() : user.id(), role, requiredAction);
                }
            }
        }
        return Map.of(
                "requiredAction", requiredAction,
                "roles", roles,
                "scannedUsers", scanned.get(),
                "updatedUsers", changed.get(),
                "defaultActionEnabled", defaultActionChanged
        );
    }

    private String normalizeRequiredAction(String requiredAction) {
        if (requiredAction == null || requiredAction.isBlank()) {
            return "CONFIGURE_TOTP";
        }
        return requiredAction.trim().toUpperCase(Locale.ROOT);
    }
}
