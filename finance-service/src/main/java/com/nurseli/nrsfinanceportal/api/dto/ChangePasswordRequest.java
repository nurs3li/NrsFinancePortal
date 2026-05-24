package com.nurseli.nrsfinanceportal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Şifre değiştirme request'i; mevcut ve yeni şifreyi taşır.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword
) {
}
