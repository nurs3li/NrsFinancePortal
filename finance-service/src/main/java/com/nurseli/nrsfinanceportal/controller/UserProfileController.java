package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.ChangePasswordRequest;
import com.nurseli.nrsfinanceportal.common.dto.EmailChangeConfirmBody;
import com.nurseli.nrsfinanceportal.common.dto.EmailChangeRequestBody;
import com.nurseli.nrsfinanceportal.common.dto.UpdateUserProfileRequest;
import com.nurseli.nrsfinanceportal.common.dto.UpdateUsernameRequest;
import com.nurseli.nrsfinanceportal.common.dto.UserResponse;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PatchMapping("/username")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUsername(@Valid @RequestBody UpdateUsernameRequest body) {
        return ResponseEntity.ok(ApiResponse.success(userProfileService.updateUsername(body.username())));
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(@Valid @RequestBody UpdateUserProfileRequest body) {
        return ResponseEntity.ok(ApiResponse.success(
                userProfileService.updateFullName(body.firstName(), body.lastName())));
    }

    @PostMapping("/email/request-code")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> requestEmailCode(@Valid @RequestBody EmailChangeRequestBody body) {
        userProfileService.requestEmailChangeCode(body.email());
        return ResponseEntity.ok(ApiResponse.success("Doğrulama kodu gönderildi."));
    }

    @PostMapping("/email/confirm")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> confirmEmail(@Valid @RequestBody EmailChangeConfirmBody body) {
        return ResponseEntity.ok(ApiResponse.success(
                userProfileService.confirmEmailChange(body.email(), body.code())));
    }

    @PostMapping("/password")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        userProfileService.changePassword(body.currentPassword(), body.newPassword());
        return ResponseEntity.ok(ApiResponse.success("Şifre güncellendi."));
    }
}
