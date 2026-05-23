package com.nurseli.nrsfinanceportal.api.dto;

import jakarta.validation.constraints.Size;

/**
 * Kullanıcı profil güncelleme request'i; ad ve soyad bilgisini taşır.
 */
public record UpdateUserProfileRequest(
        @Size(max = 128) String firstName,
        @Size(max = 128) String lastName
) {
}
