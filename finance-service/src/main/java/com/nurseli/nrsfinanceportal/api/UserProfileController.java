package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.ChangePasswordRequest;
import com.nurseli.nrsfinanceportal.api.dto.EmailChangeConfirmBody;
import com.nurseli.nrsfinanceportal.api.dto.EmailChangeRequestBody;
import com.nurseli.nrsfinanceportal.api.dto.UpdateUserProfileRequest;
import com.nurseli.nrsfinanceportal.api.dto.UpdateUsernameRequest;
import com.nurseli.nrsfinanceportal.api.dto.UserResponse;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.application.user.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Oturum açmış kullanıcının profil, e-posta ve şifre yönetimi endpoint'lerini sunar.
 */
@RestController
@RequestMapping({"/api/v1/users/me", "/api/users/me"})
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * {@code updateUsername} — Keycloak ve yerel kayıtta kullanıcı adını günceller.
     */
    @PatchMapping("/username")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUsername(@Valid @RequestBody UpdateUsernameRequest body) {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.updateUsername(body.username())));
    }

    /**
     * {@code updateProfile} — Ad ve soyad alanlarını günceller.
     */
    @PatchMapping("/profile")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(@Valid @RequestBody UpdateUserProfileRequest body) {
        return ResponseEntity.ok(ApiResponse.success(
                userProfileService.updateFullName(body.firstName(), body.lastName())));
    }

    /**
     * {@code requestEmailCode} — Yeni e-posta adresine doğrulama kodu gönderir.
     */
    @PostMapping("/email/request-code")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> requestEmailCode(@Valid @RequestBody EmailChangeRequestBody body) {
        userProfileService.requestEmailChangeCode(body.email());
        return ResponseEntity.ok(ApiResponse.success("Doğrulama kodu gönderildi."));
    }

    /**
     * {@code confirmEmail} — Doğrulama kodu ile e-posta değişikliğini onaylar.
     */
    @PostMapping("/email/confirm")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> confirmEmail(@Valid @RequestBody EmailChangeConfirmBody body) {
        return ResponseEntity.ok(ApiResponse.success(
                userProfileService.confirmEmailChange(body.email(), body.code())));
    }

    /**
     * {@code changePassword} — Mevcut şifre doğrulaması sonrası Keycloak şifresini günceller.
     */
    @PostMapping("/password")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        userProfileService.changePassword(body.currentPassword(), body.newPassword());
        return ResponseEntity.ok(ApiResponse.success("Şifre güncellendi."));
    }
}
