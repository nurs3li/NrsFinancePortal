package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.user.User;
import lombok.Getter;

@Getter
public class UserResponse {

    private final Long id;
    private final String username;
    private final String email;
    private final String role;
    private final boolean loginSuspended;

    private UserResponse(Long id, String username, String email, String role, boolean loginSuspended) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
        this.loginSuspended = loginSuspended;
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.isLoginSuspended()
        );
    }
}
