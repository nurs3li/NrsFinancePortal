package com.nurseli.nrsfinanceportal.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailChangeConfirmBody(
        @NotBlank @Email String email,
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "Kod 6 haneli olmalıdır")
        String code
) {
}
