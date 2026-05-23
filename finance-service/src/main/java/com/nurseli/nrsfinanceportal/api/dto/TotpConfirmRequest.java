package com.nurseli.nrsfinanceportal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * TOTP kurulum/doğrulama onay request'i; 6 haneli doğrulama kodunu taşır.
 */
public record TotpConfirmRequest(
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Doğrulama kodu 6 haneli olmalıdır")
        String code
) {}
