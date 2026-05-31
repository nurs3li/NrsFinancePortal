package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.api.dto.TotpSetupDto;
import com.nurseli.nrsfinanceportal.api.dto.TotpStatusDto;
import com.nurseli.nrsfinanceportal.config.KeycloakSecurityProperties;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakAdminTokenProvider;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakRealmSecurityClient;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakTotpCredentialClient;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * finance-service kullanıcı TOTP servisi — Google Authenticator kurulum, onaylama ve devre dışı bırakma akışını yönetir.
 */
@RequiredArgsConstructor
@Service

public class UserTotpService {

    private static final String ISSUER = "NRS Finance";
    private static final Duration SETUP_TTL = Duration.ofMinutes(10);

    private final CurrentUserResolver currentUserResolver;
    private final KeycloakAdminTokenProvider keycloakAdminTokenProvider;
    private final KeycloakRealmSecurityClient keycloakRealmSecurityClient;
    private final KeycloakTotpCredentialClient keycloakTotpCredentialClient;
    private final KeycloakSecurityProperties keycloakSecurityProperties;
    private final StringRedisTemplate redis;
    private final UserTotpCredentialStore totpCredentialStore;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

    /**
     * {@code status} — Kullanıcının TOTP etkin ve kurulum bekliyor durumlarını döner.
     */
    public TotpStatusDto status() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        keycloakAdminTokenProvider.requireConfigured();
    boolean enabled = totpCredentialStore.hasSecret(user.getKeycloakUserId())
                || keycloakRealmSecurityClient.hasOtpCredential(user.getKeycloakUserId());
        boolean pending = Boolean.TRUE.equals(redis.hasKey(setupKey(user.getKeycloakUserId())));
        return new TotpStatusDto(enabled, pending);
    }

    /**
     * {@code beginSetup} — Yeni TOTP secret üretir, geçici Redis'e yazar ve otpauth URL'si döner.
     */
    public TotpSetupDto beginSetup() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        keycloakAdminTokenProvider.requireConfigured();
    String keycloakUserId = user.getKeycloakUserId();

        if (totpCredentialStore.hasSecret(keycloakUserId)
                || keycloakRealmSecurityClient.hasOtpCredential(keycloakUserId)) {
            throw new IllegalStateException("İki aşamalı doğrulama zaten etkin.");
        }

        String secret = secretGenerator.generate();
        redis.opsForValue().set(setupKey(keycloakUserId), secret, SETUP_TTL);

        String accountName = StringUtils.hasText(user.getEmail()) ? user.getEmail() : user.getUsername();
        String otpauthUrl = buildOtpAuthUrl(accountName, secret);
        return new TotpSetupDto(secret, otpauthUrl, ISSUER, accountName);
    }

    /**
     * {@code confirmSetup} — Kurulum kodunu doğrular, secret'ı kalıcı kaydeder ve Keycloak required action temizler.
     */
    public void confirmSetup(String code) {
        if (!StringUtils.hasText(code) || !code.trim().matches("^\\d{6}$")) {
            throw new IllegalArgumentException("Doğrulama kodu 6 haneli olmalıdır.");
    }

        User user = currentUserResolver.getOrCreateCurrentUser();
        keycloakAdminTokenProvider.requireConfigured();
        String keycloakUserId = user.getKeycloakUserId();

        if (totpCredentialStore.hasSecret(keycloakUserId)
                || keycloakRealmSecurityClient.hasOtpCredential(keycloakUserId)) {
            throw new IllegalStateException("İki aşamalı doğrulama zaten etkin.");
        }

        String secret = redis.opsForValue().get(setupKey(keycloakUserId));
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("Kurulum süresi doldu. Lütfen yeniden başlatın.");
        }

        if (!codeVerifier.isValidCode(secret, code.trim())) {
            throw new IllegalArgumentException("Doğrulama kodu geçersiz. Authenticator saatinizi kontrol edin.");
        }

        totpCredentialStore.saveSecret(keycloakUserId, secret);
        // 2FA yalnızca portal Redis'inde tutulur. Keycloak OTP credential girişi bozar
        // (reset-password + type=otp güvenilir değil; kapat/aç sonrası şifre hatası üretir).
        keycloakTotpCredentialClient.deleteOtpCredentials(keycloakUserId);
        String requiredAction = requiredActionAlias();
        keycloakRealmSecurityClient.removeRequiredActionIfPresent(keycloakUserId, requiredAction);
        redis.delete(setupKey(keycloakUserId));
    }

    /**
     * {@code disable} — TOTP secret'ını portal ve Keycloak'tan kaldırır.
     */
    public void disable() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        keycloakAdminTokenProvider.requireConfigured();
    String keycloakUserId = user.getKeycloakUserId();

        if (!totpCredentialStore.hasSecret(keycloakUserId)
                && !keycloakRealmSecurityClient.hasOtpCredential(keycloakUserId)) {
            throw new IllegalStateException("İki aşamalı doğrulama zaten kapalı.");
        }

        totpCredentialStore.deleteSecret(keycloakUserId);
        keycloakTotpCredentialClient.deleteOtpCredentials(keycloakUserId);
        redis.delete(setupKey(keycloakUserId));
    }

    /**
     * {@code cancelSetup} — Devam eden TOTP kurulum oturumunu iptal eder.
     */
    public void cancelSetup() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        redis.delete(setupKey(user.getKeycloakUserId()));
    }

    private String setupKey(String keycloakUserId) {
        return "totp:setup:" + keycloakUserId;
    }

    private String requiredActionAlias() {
        String alias = keycloakSecurityProperties.getRequiredAction();
        return StringUtils.hasText(alias) ? alias : "CONFIGURE_TOTP";
    }

    private static String buildOtpAuthUrl(String accountName, String secret) {
        String label = URLEncoder.encode(ISSUER + ":" + accountName, StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer
                + "&algorithm=SHA1&digits=6&period=30";
    }
}
