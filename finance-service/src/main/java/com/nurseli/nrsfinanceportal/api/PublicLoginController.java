package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.application.auth.PublicLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kimlik doğrulama gerektirmeyen Keycloak tabanlı giriş ve token yenileme endpoint'lerini sunar.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/public", "/api/public"})
public class PublicLoginController {

    private final PublicLoginService publicLoginService;

    /**
     * {@code refresh} — Refresh token ile yeni access token çifti üretir.
     */
    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<LoginResponseBody>> refresh(@RequestBody RefreshRequestBody body) {
        PublicLoginService.PublicLoginResult result = publicLoginService.refresh(body.refreshToken());
        return toResponse(result);
    }

    /**
     * {@code login} — Kullanıcı adı/e-posta, şifre ve opsiyonel OTP ile Keycloak oturumu açar.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseBody>> login(@RequestBody LoginRequestBody body) {
        PublicLoginService.PublicLoginResult result = publicLoginService.login(
                body.usernameOrEmail(),
                body.password(),
                body.otp(),
                body.rememberMe()
        );
        return toResponse(result);
    }

    private static ResponseEntity<ApiResponse<LoginResponseBody>> toResponse(PublicLoginService.PublicLoginResult result) {
        if (result.otpRequired()) {
            return ResponseEntity.ok(ApiResponse.success(new LoginResponseBody(
                    true,
                    result.message(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            )));
        }
        return ResponseEntity.ok(ApiResponse.success(new LoginResponseBody(
                false,
                null,
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                result.refreshExpiresIn(),
                result.userId(),
                result.username(),
                result.email(),
                result.role()
        )));
    }

    /** Token yenileme request gövdesi. */
    public record RefreshRequestBody(String refreshToken) {}

    /** Giriş request gövdesi. */
    public record LoginRequestBody(
            String usernameOrEmail,
            String password,
            String otp,
            boolean rememberMe
    ) {}

    /** Giriş veya OTP gerekli yanıt gövdesi. */
    public record LoginResponseBody(
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
    ) {}
}
