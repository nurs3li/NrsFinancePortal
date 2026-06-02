package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.application.auth.PublicPasswordResetService;
import com.nurseli.nrsfinanceportal.application.auth.PublicPasswordResetService.PasswordResetResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-posta doğrulamalı self-servis şifre sıfırlama endpoint'lerini sunar.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/public/password-reset", "/api/public/password-reset"})
public class PublicPasswordResetController {

    private final PublicPasswordResetService publicPasswordResetService;

    /**
     * {@code requestCode} — Kayıtlı e-postaya şifre sıfırlama doğrulama kodu gönderir.
     */
    @PostMapping("/request-code")
    public ResponseEntity<ApiResponse<String>> requestCode(@RequestBody RequestCodeBody body) {
        publicPasswordResetService.requestCode(body.email());
        return ResponseEntity.ok(ApiResponse.success("Doğrulama kodu gönderildi."));
    }

    /**
     * {@code verifyCode} — Doğrulama kodunu onaylar ve şifre belirleme oturumu açar.
     */
    @PostMapping("/verify-code")
    public ResponseEntity<ApiResponse<String>> verifyCode(@RequestBody VerifyCodeBody body) {
        publicPasswordResetService.verifyCode(body.email(), body.code());
        return ResponseEntity.ok(ApiResponse.success("Doğrulama kodu onaylandı."));
    }

    /**
     * {@code complete} — Yeni şifreyi Keycloak'ta ayarlar.
     */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<CompleteResponse>> complete(@RequestBody CompleteBody body) {
        PasswordResetResult result = publicPasswordResetService.completeReset(
                body.email(),
                body.newPassword(),
                body.confirmPassword()
        );
        return ResponseEntity.ok(ApiResponse.success(
                new CompleteResponse(result.username(), result.message())));
    }

    /** Doğrulama kodu isteği gövdesi. */
    public record RequestCodeBody(String email) {}

    /** Kod doğrulama isteği gövdesi. */
    public record VerifyCodeBody(String email, String code) {}

    /** Şifre sıfırlama tamamlama isteği gövdesi. */
    public record CompleteBody(String email, String newPassword, String confirmPassword) {}

    /** Şifre sıfırlama tamamlama yanıtı. */
    public record CompleteResponse(String username, String message) {}
}
