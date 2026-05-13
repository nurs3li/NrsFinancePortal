package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AssignRoleRequest(
        @NotBlank
        @Pattern(regexp = "USER", message = "Geçerli rol: USER (ADMIN atanamaz)")
        String role
) {
    public Role toDomainRole() {
        return Role.valueOf(role);
    }
}
