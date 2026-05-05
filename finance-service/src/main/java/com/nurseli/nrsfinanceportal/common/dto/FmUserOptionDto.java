package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.user.User;

public record FmUserOptionDto(long id, String username, String email, String keycloakUserId) {
    public static FmUserOptionDto from(User u) {
        return new FmUserOptionDto(u.getId(), u.getUsername(), u.getEmail(), u.getKeycloakUserId());
    }
}
