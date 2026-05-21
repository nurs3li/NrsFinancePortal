package com.nurseli.nrsfinanceportal.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailChangeRequestBody(
        @NotBlank @Email String email
) {
}
