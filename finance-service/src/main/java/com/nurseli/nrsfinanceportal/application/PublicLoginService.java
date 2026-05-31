package com.nurseli.nrsfinanceportal.application;

import com.nurseli.nrsfinanceportal.api.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.api.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakTokenClient;
import com.nurseli.nrsfinanceportal.infrastructure.keycloak.KeycloakTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * finance-service public login servisi — Keycloak token alımı, OTP politikası ve kullanıcı provizyonunu birleştirir.
 */
@RequiredArgsConstructor
@Service

public class PublicLoginService {

    private final KeycloakTokenClient keycloakTokenClient;
    private final UserSyncService userSyncService;
    private final KeycloakTotpLoginPolicyService loginPolicy;
    private final UserTotpCredentialStore totpCredentialStore;

    /**
     * {@code refresh} — Refresh token ile yeni access token alır, kullanıcıyı senkronlar ve askıya alınmış hesabı reddeder.
     */
    public PublicLoginResult refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Refresh token zorunludur.");
    }
        try {
            KeycloakTokenResponse tokens = keycloakTokenClient.refreshTokens(refreshToken);
            User user = userSyncService.provisionFromAccessToken(tokens.accessToken());
            if (user.isLoginSuspended()) {
                throw new ApiBusinessException(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED,
                        "Hesabınız askıya alındı. Erişim için destek ile iletişime geçin.");
            }
            return PublicLoginResult.success(tokens, user);
        } catch (KeycloakTokenClient.KeycloakTokenException ex) {
            return mapTokenError(ex, null, false, false);
        }
    }

    /**
     * {@code login} — Kullanıcı adı/şifre (ve gerekiyorsa OTP) ile Keycloak'tan token alır; portal TOTP veya Keycloak OTP akışını yönetir.
     */
    public PublicLoginResult login(String usernameOrEmail, String password, String otp, boolean rememberMe) {
        if (!StringUtils.hasText(usernameOrEmail) || !StringUtils.hasText(password)) {
            throw new ApiBusinessException(HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST,
    "Kullanıcı adı ve şifre zorunludur.");
        }

        String loginId = normalizeLoginId(usernameOrEmail);
        String keycloakLoginName = loginPolicy.resolveLoginUsername(loginId);
        String keycloakUserId = loginPolicy.resolveKeycloakUserId(loginId).orElse(null);
        String keycloakOtp = otp;
        boolean portalOtpVerified = false;

        if (!StringUtils.hasText(otp)) {
            loginPolicy.prepareLogin(loginId);
            if (loginPolicy.requiresOtpForLogin(loginId)) {
                return PublicLoginResult.otpRequired("İki aşamalı doğrulama kodu gerekli.");
            }
        } else if (keycloakUserId != null && totpCredentialStore.hasSecret(keycloakUserId)) {
            if (!totpCredentialStore.verifyCode(keycloakUserId, otp)) {
                throw new ApiBusinessException(HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_CREDENTIALS,
                        "İki aşamalı doğrulama kodu hatalı.");
            }
            portalOtpVerified = true;
            keycloakOtp = null;
        }

        try {
            KeycloakTokenResponse tokens = keycloakTokenClient.obtainTokens(
                    keycloakLoginName, password, keycloakOtp, rememberMe);
            if (!portalOtpVerified && !StringUtils.hasText(otp)) {
                String authenticatedUserId = userSyncService.extractSubject(tokens.accessToken());
                if (loginPolicy.requiresOtpForKeycloakUserId(authenticatedUserId)) {
                    return PublicLoginResult.otpRequired("İki aşamalı doğrulama kodu gerekli.");
                }
            }
            User user = userSyncService.provisionFromAccessToken(tokens.accessToken());
            if (user.isLoginSuspended()) {
                throw new ApiBusinessException(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED,
                        "Hesabınız askıya alındı. Erişim için destek ile iletişime geçin.");
            }
            return PublicLoginResult.success(tokens, user);
        } catch (KeycloakTokenClient.KeycloakTokenException ex) {
            if (portalOtpVerified) {
                throw new ApiBusinessException(HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_CREDENTIALS,
                        "Doğrulama kodu kabul edildi. Şifre hatalı — «Şifremi unuttum» ile belirlediğiniz güncel şifreyi girin.");
            }
            if (!StringUtils.hasText(otp)
                    && ex.error().kind() == KeycloakTokenClient.TokenErrorKind.INVALID_CREDENTIALS
                    && loginPolicy.requiresOtpForLogin(loginId)) {
                return PublicLoginResult.otpRequired("İki aşamalı doğrulama kodu gerekli.");
            }
            return mapTokenError(ex, loginId, StringUtils.hasText(otp), portalOtpVerified);
        }
    }

    private PublicLoginResult mapTokenError(
            KeycloakTokenClient.KeycloakTokenException ex,
            String loginId,
            boolean otpProvided,
            boolean portalOtpVerified) {
        KeycloakTokenClient.TokenError err = ex.error();
        return switch (err.kind()) {
            case OTP_REQUIRED -> PublicLoginResult.otpRequired(err.message());
            case ACCOUNT_DISABLED -> throw new ApiBusinessException(HttpStatus.FORBIDDEN, ApiErrorCode.ACCESS_DENIED,
                    err.message());
            case INVALID_CREDENTIALS -> mapInvalidCredentials(loginId, otpProvided, portalOtpVerified, err.message());
            case OTHER -> throw new ApiBusinessException(HttpStatus.BAD_GATEWAY, ApiErrorCode.INTERNAL_SERVER_ERROR,
                    err.message());
        };
    }

    private PublicLoginResult mapInvalidCredentials(
            String loginId,
            boolean otpProvided,
            boolean portalOtpVerified,
            String message) {
        if (portalOtpVerified) {
            throw new ApiBusinessException(HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_CREDENTIALS,
                    "Doğrulama kodu kabul edildi. Şifre hatalı — «Şifremi unuttum» ile belirlediğiniz güncel şifreyi girin.");
        }
        if (otpProvided) {
            throw new ApiBusinessException(HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_CREDENTIALS,
                    "İki aşamalı doğrulama kodu hatalı.");
        }
        if (loginId != null && loginPolicy.requiresOtpForLogin(loginId)) {
            return PublicLoginResult.otpRequired("İki aşamalı doğrulama kodu gerekli.");
        }
        throw new ApiBusinessException(HttpStatus.UNAUTHORIZED, ApiErrorCode.INVALID_CREDENTIALS,
                "Kullanıcı adı veya şifre hatalı.");
    }

    private static String normalizeLoginId(String usernameOrEmail) {
        if (!StringUtils.hasText(usernameOrEmail)) {
            return "";
        }
        String trimmed = usernameOrEmail.trim();
        return trimmed.contains("@") ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
    }

    /**
     * PublicLoginResult — Keycloak token yanıtı ve OTP gereksinimi durumunu taşır.
     */
    public record PublicLoginResult(
            boolean otpRequired,
            String message,
            String accessToken,
            String refreshToken,
            Integer expiresIn,
            Integer refreshExpiresIn,
            Long userId,
            String username,
            String email,
            String role
    ) {
        static PublicLoginResult otpRequired(String message) {
            return new PublicLoginResult(true, message, null, null, null, null, null, null, null, null);
        }

        static PublicLoginResult success(KeycloakTokenResponse tokens, User user) {
            return new PublicLoginResult(
                    false,
                    null,
                    tokens.accessToken(),
                    tokens.refreshToken(),
                    tokens.expiresIn(),
                    tokens.refreshExpiresIn(),
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole().name()
            );
        }
    }
}
