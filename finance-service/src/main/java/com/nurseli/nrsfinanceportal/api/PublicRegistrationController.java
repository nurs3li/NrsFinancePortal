package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.application.PublicRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-posta doğrulamalı self-servis kullanıcı kaydı endpoint'lerini sunar.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/public/register", "/api/public/register"})
public class PublicRegistrationController {

    private final PublicRegistrationService publicRegistrationService;

    /**
     * {@code requestCode} — Kayıt e-postasına doğrulama kodu gönderir.
     */
    @PostMapping("/request-code")
    public ResponseEntity<ApiResponse<String>> requestCode(@RequestBody RequestCodeBody body) {
        publicRegistrationService.requestCode(body.email());
        return ResponseEntity.ok(ApiResponse.success("Doğrulama kodu gönderildi."));
    }

    /**
     * {@code complete} — Doğrulama kodu ile Keycloak ve yerel kullanıcı kaydını tamamlar.
     */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<String>> complete(@RequestBody CompleteRegistrationBody body) {
        publicRegistrationService.completeRegistration(
                body.email(),
                body.username(),
                body.firstName(),
                body.lastName(),
                body.password(),
                body.code()
        );
        return ResponseEntity.ok(ApiResponse.success("Kayıt tamamlandı. Giriş yapabilirsiniz."));
    }

    /** Doğrulama kodu isteği gövdesi. */
    public record RequestCodeBody(String email) {}

    /** Kayıt tamamlama request gövdesi. */
    public record CompleteRegistrationBody(
            String email,
            String username,
            String firstName,
            String lastName,
            String password,
            String code
    ) {}
}
