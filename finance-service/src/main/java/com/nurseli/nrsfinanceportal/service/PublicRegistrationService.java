package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakRealmRoleMappingClient;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakUserRegistrationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PublicRegistrationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,32}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final RegistrationEmailSender registrationEmailSender;
    private final KeycloakUserRegistrationClient keycloakUserRegistrationClient;
    private final KeycloakRealmRoleMappingClient keycloakRealmRoleMappingClient;

    public void requestCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);

        String cooldownKey = cooldownKey(normalizedEmail);
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            throw new IllegalStateException("Kod zaten gönderildi. Lütfen kısa süre sonra tekrar deneyin.");
        }

        String code = generateSixDigitCode();
        redis.opsForValue().set(codeKey(normalizedEmail), code, CODE_TTL);
        redis.opsForValue().set(cooldownKey, "1", RESEND_COOLDOWN);
        registrationEmailSender.sendVerificationCode(normalizedEmail, code);
    }

    public void completeRegistration(String email, String username, String password, String code) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedUsername = normalizeUsername(username);
        validateEmail(normalizedEmail);
        validateUsername(normalizedUsername);
        validatePassword(password);
        validateCode(code);

        String expectedCode = redis.opsForValue().get(codeKey(normalizedEmail));
        if (!StringUtils.hasText(expectedCode) || !expectedCode.equals(code.trim())) {
            throw new IllegalStateException("Doğrulama kodu geçersiz veya süresi doldu.");
        }

        String keycloakUserId = keycloakUserRegistrationClient.createUser(
                normalizedUsername,
                normalizedEmail,
                password,
                true,
                List.of("CONFIGURE_TOTP")
        );
        keycloakRealmRoleMappingClient.replaceApplicationRealmRole(keycloakUserId, Role.USER);
        redis.delete(codeKey(normalizedEmail));
        redis.delete(cooldownKey(normalizedEmail));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private static void validateEmail(String email) {
        if (!StringUtils.hasText(email) || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Geçerli bir e-posta adresi girin.");
        }
    }

    private static void validateUsername(String username) {
        if (!StringUtils.hasText(username) || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("Kullanıcı adı 3-32 karakter olmalı ve yalnızca harf/rakam/._- içerebilir.");
        }
    }

    private static void validatePassword(String password) {
        if (!StringUtils.hasText(password) || password.length() < 8) {
            throw new IllegalArgumentException("Şifre en az 8 karakter olmalı.");
        }
    }

    private static void validateCode(String code) {
        if (!StringUtils.hasText(code) || !code.trim().matches("\\d{6}")) {
            throw new IllegalArgumentException("Kod 6 haneli olmalıdır.");
        }
    }

    private static String generateSixDigitCode() {
        int value = 100000 + RANDOM.nextInt(900000);
        return Integer.toString(value);
    }

    private static String codeKey(String email) {
        return "register:code:" + email;
    }

    private static String cooldownKey(String email) {
        return "register:cooldown:" + email;
    }
}
