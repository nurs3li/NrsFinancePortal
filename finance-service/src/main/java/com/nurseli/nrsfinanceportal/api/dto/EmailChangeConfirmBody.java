package com.nurseli.nrsfinanceportal.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Email değişikliği onay request'i; yeni email ve 6 haneli doğrulama kodunu taşır.
 */
public record EmailChangeConfirmBody(
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "Kod 6 haneli olmalıdır")
        String code
) {
}
