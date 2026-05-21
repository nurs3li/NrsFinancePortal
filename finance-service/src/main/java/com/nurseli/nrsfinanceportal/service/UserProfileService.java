package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.UserResponse;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakPasswordGrantClient;
import com.nurseli.nrsfinanceportal.integration.keycloak.KeycloakUserProfileClient;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,32}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Kullanıcı kodu bu süre içinde girmeli (UI geri sayımı ile uyumlu). */
    private static final Duration PROFILE_EMAIL_CODE_TTL = Duration.ofSeconds(60);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final CurrentUserResolver currentUserResolver;
    private final UserRepository userRepository;
    private final KeycloakUserProfileClient keycloakUserProfileClient;
    private final KeycloakPasswordGrantClient keycloakPasswordGrantClient;
    private final RegistrationEmailSender registrationEmailSender;
    private final StringRedisTemplate redis;

    @Transactional
    public UserResponse updateUsername(String username) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        String normalized = normalizeUsername(username);
        validateUsername(normalized);

        if (normalized.equalsIgnoreCase(safe(user.getUsername()))) {
            return UserResponse.from(user);
        }
        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalized, user.getId())) {
            throw new IllegalArgumentException("Bu kullanıcı adı zaten kullanılıyor.");
        }

        keycloakUserProfileClient.requireConfigured();
        keycloakUserProfileClient.updateUsername(user.getKeycloakUserId(), normalized);
        user.setUsername(normalized);
        userRepository.save(user);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateFullName(String firstName, String lastName) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        String fn = trimToNull(firstName);
        String ln = trimToNull(lastName);
        if (fn == null && ln == null) {
            throw new IllegalArgumentException("Ad veya soyad girilmelidir.");
        }

        keycloakUserProfileClient.requireConfigured();
        keycloakUserProfileClient.updateFullName(user.getKeycloakUserId(), fn, ln);
        user.setFirstName(fn);
        user.setLastName(ln);
        userRepository.save(user);
        return UserResponse.from(user);
    }

    public void requestEmailChangeCode(String email) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        String normalized = normalizeEmail(email);
        validateEmail(normalized);

        if (normalized.equalsIgnoreCase(safe(user.getEmail()))) {
            throw new IllegalArgumentException("Yeni e-posta mevcut adresinizle aynı olamaz.");
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalized, user.getId())) {
            throw new IllegalArgumentException("Bu e-posta adresi zaten kullanılıyor.");
        }

        String cooldownKey = emailCooldownKey(user.getId(), normalized);
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            throw new IllegalStateException("Kod zaten gönderildi. Lütfen kısa süre sonra tekrar deneyin.");
        }

        String code = generateSixDigitCode();
        redis.opsForValue().set(emailCodeKey(user.getId(), normalized), code, PROFILE_EMAIL_CODE_TTL);
        redis.opsForValue().set(cooldownKey, "1", RESEND_COOLDOWN);
        registrationEmailSender.sendEmailChangeVerificationCode(normalized, code);
    }

    @Transactional
    public UserResponse confirmEmailChange(String email, String code) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        String normalized = normalizeEmail(email);
        validateEmail(normalized);
        validateCode(code);

        if (normalized.equalsIgnoreCase(safe(user.getEmail()))) {
            throw new IllegalArgumentException("Yeni e-posta mevcut adresinizle aynı olamaz.");
        }
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalized, user.getId())) {
            throw new IllegalArgumentException("Bu e-posta adresi zaten kullanılıyor.");
        }

        String expected = redis.opsForValue().get(emailCodeKey(user.getId(), normalized));
        if (!StringUtils.hasText(expected) || !expected.equals(code.trim())) {
            throw new IllegalStateException("Doğrulama kodu geçersiz veya süresi doldu.");
        }

        keycloakUserProfileClient.requireConfigured();
        keycloakUserProfileClient.updateEmail(user.getKeycloakUserId(), normalized, true);
        user.setEmail(normalized);
        user.setEmailVerified(true);
        userRepository.save(user);

        redis.delete(emailCodeKey(user.getId(), normalized));
        redis.delete(emailCooldownKey(user.getId(), normalized));
        return UserResponse.from(user);
    }

    public void changePassword(String currentPassword, String newPassword) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        validatePassword(newPassword);
        if (currentPassword != null && currentPassword.equals(newPassword)) {
            throw new IllegalArgumentException("Yeni şifre mevcut şifreyle aynı olamaz.");
        }

        String loginId = safe(user.getUsername());
        if (!StringUtils.hasText(loginId)) {
            loginId = safe(user.getEmail());
        }
        if (!StringUtils.hasText(loginId)) {
            throw new IllegalStateException("Giriş bilgisi bulunamadı.");
        }

        boolean valid = keycloakPasswordGrantClient.verifyCredentials(loginId, currentPassword);
        if (!valid) {
            throw new IllegalArgumentException("Mevcut şifre hatalı.");
        }

        keycloakUserProfileClient.requireConfigured();
        keycloakUserProfileClient.setPassword(user.getKeycloakUserId(), newPassword);
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

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String generateSixDigitCode() {
        int value = 100000 + RANDOM.nextInt(900000);
        return Integer.toString(value);
    }

    private static String emailCodeKey(Long userId, String email) {
        return "profile:email-code:" + userId + ":" + email;
    }

    private static String emailCooldownKey(Long userId, String email) {
        return "profile:email-cooldown:" + userId + ":" + email;
    }
}
