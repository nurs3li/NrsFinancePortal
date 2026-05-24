package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.TotpConfirmRequest;
import com.nurseli.nrsfinanceportal.api.dto.TotpSetupDto;
import com.nurseli.nrsfinanceportal.api.dto.TotpStatusDto;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.application.UserTotpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kullanıcı TOTP (iki aşamalı doğrulama) kurulum ve yönetim endpoint'lerini sunar.
 */
@RestController
@RequestMapping("/api/users/me/totp")
@RequiredArgsConstructor
public class UserTotpController {

    private final UserTotpService userTotpService;

    /**
     * {@code status} — TOTP etkinlik durumunu döner.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TotpStatusDto>> status() {
        return ResponseEntity.ok(ApiResponse.success(userTotpService.status()));
    }

    /**
     * {@code beginSetup} — QR/secret içeren TOTP kurulum DTO'su üretir.
     */
    @PostMapping("/setup")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TotpSetupDto>> beginSetup() {
        return ResponseEntity.ok(ApiResponse.success(userTotpService.beginSetup()));
    }

    /**
     * {@code confirm} — OTP kodu ile TOTP kurulumunu Keycloak'ta etkinleştirir.
     */
    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> confirm(@Valid @RequestBody TotpConfirmRequest body) {
        userTotpService.confirmSetup(body.code());
        return ResponseEntity.ok(ApiResponse.success("İki aşamalı doğrulama etkinleştirildi."));
    }

    /**
     * {@code cancelSetup} — Tamamlanmamış TOTP kurulumunu iptal eder.
     */
    @PostMapping("/cancel")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> cancelSetup() {
        userTotpService.cancelSetup();
        return ResponseEntity.ok(ApiResponse.success("Kurulum iptal edildi."));
    }

    /**
     * {@code disable} — Etkin TOTP credential'ını Keycloak'tan kaldırır.
     */
    @DeleteMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<String>> disable() {
        userTotpService.disable();
        return ResponseEntity.ok(ApiResponse.success("İki aşamalı doğrulama devre dışı bırakıldı."));
    }
}
