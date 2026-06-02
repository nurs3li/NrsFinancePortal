package com.nurseli.nrsfinanceportal.application.user;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * finance-service TOTP credential deposu — onaylanmış TOTP secret'ını Redis'te saklar ve doğrular.
 */
@RequiredArgsConstructor
@Service

public class UserTotpCredentialStore {

    private static final String SECRET_KEY_PREFIX = "totp:secret:";

    private final StringRedisTemplate redis;
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(
            new DefaultCodeGenerator(),
            new SystemTimeProvider()
    );

    /**
     * {@code hasSecret} — Kullanıcı için kayıtlı TOTP secret olup olmadığını kontrol eder.
     */
    public boolean hasSecret(String keycloakUserId) {
        if (!StringUtils.hasText(keycloakUserId)) {
            return false;
    }
        return Boolean.TRUE.equals(redis.hasKey(secretKey(keycloakUserId)));
    }

    /**
     * {@code saveSecret} — Base32 TOTP secret'ını Redis'e kalıcı olarak yazar.
     */
    public void saveSecret(String keycloakUserId, String base32Secret) {
        if (!StringUtils.hasText(keycloakUserId) || !StringUtils.hasText(base32Secret)) {
            throw new IllegalArgumentException("TOTP secret kaydedilemedi.");
    }
        redis.opsForValue().set(secretKey(keycloakUserId), base32Secret.trim());
    }

    /**
     * {@code deleteSecret} — Kullanıcının TOTP secret kaydını Redis'ten siler.
     */
    public void deleteSecret(String keycloakUserId) {
        if (!StringUtils.hasText(keycloakUserId)) {
            return;
    }
        redis.delete(secretKey(keycloakUserId));
    }

    /**
     * {@code verifyCode} — Verilen 6 haneli kodu saklanan secret ile TOTP algoritmasıyla doğrular.
     */
    public boolean verifyCode(String keycloakUserId, String code) {
        if (!StringUtils.hasText(keycloakUserId) || !StringUtils.hasText(code)) {
            return false;
    }
        String secret = redis.opsForValue().get(secretKey(keycloakUserId));
        if (!StringUtils.hasText(secret)) {
            return false;
        }
        return codeVerifier.isValidCode(secret, code.trim());
    }

    private static String secretKey(String keycloakUserId) {
        return SECRET_KEY_PREFIX + keycloakUserId;
    }
}
