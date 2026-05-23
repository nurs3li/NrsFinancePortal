package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakAdminTokenProvider;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakRealmSecurityClient;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserLookupClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * finance-service TOTP giriş politikası — 2FA yalnızca kullanıcı kurduysa istenir, otomatik CONFIGURE_TOTP zorunluluğu temizlenir.
 */
@Slf4j
@RequiredArgsConstructor
@Service

public class KeycloakTotpLoginPolicyService {

    private static final String CONFIGURE_TOTP = "CONFIGURE_TOTP";

    private final KeycloakAdminTokenProvider tokenProvider;
    private final KeycloakUserLookupClient userLookupClient;
    private final KeycloakRealmSecurityClient realmSecurityClient;
    private final UserTotpCredentialStore totpCredentialStore;

    /**
     * {@code resolveKeycloakUserId} — Kullanıcı adı veya e-postadan Keycloak user ID'sini çözümler.
     */
    public java.util.Optional<String> resolveKeycloakUserId(String usernameOrEmail) {
        return resolveUserId(usernameOrEmail);
    }

    /**
     * {@code resolveLoginUsername} — Keycloak'ta giriş için kullanılacak kullanıcı adını döner.
     */
    public String resolveLoginUsername(String usernameOrEmail) {
        return userLookupClient.resolveLoginUsername(usernameOrEmail);
    }

    /**
     * {@code prepareLogin} — Giriş öncesi TOTP kayıtlı değilse CONFIGURE_TOTP required action'ını temizler.
     */
    public void prepareLogin(String usernameOrEmail) {
        resolveUserId(usernameOrEmail).ifPresent(this::clearTotpSetupIfNotEnrolled);
    }

    /**
     * {@code afterRegistration} — Kayıt sonrası aynı TOTP temizliğini uygular.
     */
    public void afterRegistration(String keycloakUserId) {
        clearTotpSetupIfNotEnrolled(keycloakUserId);
    }

    /**
     * {@code requiresOtpForLogin} — Kullanıcının girişte OTP gerektirip gerektirmediğini portal secret veya Keycloak OTP credential ile kontrol eder.
     */
    public boolean requiresOtpForLogin(String usernameOrEmail) {
        return resolveUserId(usernameOrEmail)
                .map(this::requiresOtpForUser)
                .orElse(false);
    }

    private boolean requiresOtpForUser(String keycloakUserId) {
        if (totpCredentialStore.hasSecret(keycloakUserId)) {
            return true;
        }
        return hasOtpCredential(keycloakUserId);
    }

    private void clearTotpSetupIfNotEnrolled(String keycloakUserId) {
        if (!tokenProvider.isConfigured()) {
            return;
        }
        try {
            if (!requiresOtpForUser(keycloakUserId)) {
                realmSecurityClient.removeRequiredActionIfPresent(keycloakUserId, CONFIGURE_TOTP);
            }
        } catch (Exception ex) {
            log.warn("[LOGIN_POLICY] totp_setup_cleanup_failed userId={} reason={}", keycloakUserId, ex.getMessage());
        }
    }

    private boolean hasOtpCredential(String keycloakUserId) {
        try {
            return realmSecurityClient.hasOtpCredential(keycloakUserId);
        } catch (Exception ex) {
            log.warn("[LOGIN_POLICY] otp_check_failed userId={} reason={}", keycloakUserId, ex.getMessage());
            return false;
        }
    }

    private java.util.Optional<String> resolveUserId(String usernameOrEmail) {
        if (!StringUtils.hasText(usernameOrEmail)) {
            return java.util.Optional.empty();
        }
        String trimmed = usernameOrEmail.trim();
        String userId = trimmed.contains("@")
                ? userLookupClient.findUserIdByEmail(trimmed)
                : userLookupClient.findUserIdByUsername(trimmed);
        return java.util.Optional.ofNullable(userId);
    }

    /**
     * {@code disableDefaultTotpOnSignup} — Uygulama başlangıcında Keycloak'ta varsayılan CONFIGURE_TOTP required action'ını devre dışı bırakır.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void disableDefaultTotpOnSignup() {
        if (!tokenProvider.isConfigured()) {
            return;
        }
        try {
            realmSecurityClient.ensureRequiredActionDefaultDisabled(CONFIGURE_TOTP);
        } catch (Exception ex) {
            log.warn("[LOGIN_POLICY] disable_default_totp_failed reason={}", ex.getMessage());
        }
    }
}
