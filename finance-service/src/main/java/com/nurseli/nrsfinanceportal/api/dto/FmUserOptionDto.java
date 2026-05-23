package com.nurseli.nrsfinanceportal.api.dto;

import com.nurseli.nrsfinanceportal.domain.user.User;

/**
 * Finance Manager kullanıcı seçim DTO'su; dropdown ve filtre listelerinde kullanılır.
 * {@link #from(com.nurseli.nrsfinanceportal.domain.user.User)} ile domain entity'den üretilir.
 */
public record FmUserOptionDto(long id, String username, String email, String keycloakUserId) {
    public static FmUserOptionDto from(User u) {
        return new FmUserOptionDto(u.getId(), u.getUsername(), u.getEmail(), u.getKeycloakUserId());
    }
}
