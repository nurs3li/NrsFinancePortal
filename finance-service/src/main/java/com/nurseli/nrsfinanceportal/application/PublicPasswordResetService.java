package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserLookupClient;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakUserProfileClient;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * finance-service public şifre sıfırlama servisi — e-posta doğrulama kodu ve Keycloak şifre güncelleme akışını yönetir.
 */
@RequiredArgsConstructor
@Service
public class PublicPasswordResetService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final RegistrationEmailSender registrationEmailSender;
    private final KeycloakUserLookupClient keycloakUserLookupClient;
    private final KeycloakUserProfileClient keycloakUserProfileClient;
    private final UserRepository userRepository;

    /**
     * PasswordResetResult — şifre sıfırlama tamamlandığında otomatik giriş için kullanıcı adı döner.
     */
    public record PasswordResetResult(String username, String message) {}

    /**
     * {@code requestCode} — Kayıtlı aktif kullanıcıya 6 haneli doğrulama kodu gönderir; hesap yoksa sessizce başarı döner.
     */
    public void requestCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);

        String cooldownKey = cooldownKey(normalizedEmail);
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            throw new IllegalStateException("Kod zaten gönderildi. Lütfen kısa süre sonra tekrar deneyin.");
        }

        redis.opsForValue().set(cooldownKey, "1", RESEND_COOLDOWN);

        KeycloakUserLookupClient.UserLite user = keycloakUserLookupClient.findUser(normalizedEmail);
        if (user == null || !StringUtils.hasText(user.id())) {
            return;
        }
        if (isLoginSuspended(user.id())) {
            return;
        }

        String code = generateSixDigitCode();
        redis.opsForValue().set(codeKey(normalizedEmail), code, CODE_TTL);
        registrationEmailSender.sendPasswordResetVerificationCode(normalizedEmail, code);
    }

    /**
     * {@code verifyCode} — Doğrulama kodunu kontrol eder ve kısa süreli verified session oluşturur.
     */
    public void verifyCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);
        validateCode(code);

        String expectedCode = redis.opsForValue().get(codeKey(normalizedEmail));
        if (!StringUtils.hasText(expectedCode) || !expectedCode.equals(code.trim())) {
            throw new IllegalStateException("Doğrulama kodu geçersiz veya süresi doldu.");
        }

        KeycloakUserLookupClient.UserLite user = keycloakUserLookupClient.findUser(normalizedEmail);
        if (user == null || !StringUtils.hasText(user.id())) {
            throw new IllegalStateException("Doğrulama kodu geçersiz veya süresi doldu.");
        }
        if (isLoginSuspended(user.id())) {
            throw new IllegalStateException("Hesabınız askıya alındı. Erişim için destek ile iletişime geçin.");
        }

        redis.opsForValue().set(verifiedKey(normalizedEmail), user.id(), VERIFIED_TTL);
        redis.delete(codeKey(normalizedEmail));
    }

    /**
     * {@code completeReset} — Verified session sonrası yeni şifreyi Keycloak'ta ayarlar.
     */
    public PasswordResetResult completeReset(String email, String newPassword, String confirmPassword) {
        String normalizedEmail = normalizeEmail(email);
        validateEmail(normalizedEmail);
        validatePassword(newPassword);
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Şifreler eşleşmiyor.");
        }

        String keycloakUserId = redis.opsForValue().get(verifiedKey(normalizedEmail));
        if (!StringUtils.hasText(keycloakUserId)) {
            throw new IllegalStateException("Doğrulama oturumu geçersiz veya süresi doldu. Lütfen kodu yeniden alın.");
        }
        if (isLoginSuspended(keycloakUserId)) {
            throw new IllegalStateException("Hesabınız askıya alındı. Erişim için destek ile iletişime geçin.");
        }

        keycloakUserProfileClient.requireConfigured();
        keycloakUserProfileClient.setPassword(keycloakUserId, newPassword);

        String username = resolveUsername(keycloakUserId, normalizedEmail);
        clearResetKeys(normalizedEmail);

        return new PasswordResetResult(username, "Şifreniz güncellendi. Giriş yapılıyor…");
    }

    private boolean isLoginSuspended(String keycloakUserId) {
        return userRepository.findByKeycloakUserId(keycloakUserId)
                .map(User::isLoginSuspended)
                .orElse(false);
    }

    private String resolveUsername(String keycloakUserId, String fallbackEmail) {
        return userRepository.findByKeycloakUserId(keycloakUserId)
                .map(User::getUsername)
                .filter(StringUtils::hasText)
                .orElseGet(() -> {
                    KeycloakUserLookupClient.UserLite user = keycloakUserLookupClient.findUser(fallbackEmail);
                    if (user != null && StringUtils.hasText(user.username())) {
                        return user.username();
                    }
                    return fallbackEmail;
                });
    }

    private void clearResetKeys(String normalizedEmail) {
        redis.delete(codeKey(normalizedEmail));
        redis.delete(cooldownKey(normalizedEmail));
        redis.delete(verifiedKey(normalizedEmail));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateEmail(String email) {
        if (!StringUtils.hasText(email) || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Geçerli bir e-posta adresi girin.");
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
        return "password-reset:code:" + email;
    }

    private static String cooldownKey(String email) {
        return "password-reset:cooldown:" + email;
    }

    private static String verifiedKey(String email) {
        return "password-reset:verified:" + email;
    }
}
