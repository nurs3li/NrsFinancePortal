package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.user.User;
import lombok.Getter;

import java.time.Instant;

@Getter
public class UserResponse {

    private final Long id;
    private final String username;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String role;
    private final boolean loginSuspended;
    private final Instant createdAt;

    private UserResponse(
            Long id,
            String username,
            String email,
            String firstName,
            String lastName,
            String role,
            boolean loginSuspended,
            Instant createdAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.loginSuspended = loginSuspended;
        this.createdAt = createdAt;
    }

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name(),
                user.isLoginSuspended(),
                user.getCreatedAt()
        );
    }
}
