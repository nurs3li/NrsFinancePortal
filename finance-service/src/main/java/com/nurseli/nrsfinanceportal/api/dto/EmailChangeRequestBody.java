package com.nurseli.nrsfinanceportal.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Email değişikliği başlatma request'i; hedef email adresini taşır.
 */
public record EmailChangeRequestBody(
        @NotBlank @Email String email
) {
}
