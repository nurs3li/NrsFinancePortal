package com.nurseli.nrsfinanceportal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Kullanıcı adı güncelleme request'i; yeni username değerini taşır.
 */
public record UpdateUsernameRequest(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Kullanıcı adı yalnızca harf, rakam, nokta, alt çizgi ve tire içerebilir")
        String username
) {
}
