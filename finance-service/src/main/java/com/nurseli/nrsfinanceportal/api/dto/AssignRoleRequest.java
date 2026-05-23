package com.nurseli.nrsfinanceportal.api.dto;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Kullanıcıya rol atama request'i; yalnızca USER rolü kabul edilir.
 * {@link #toDomainRole()} ile domain {@code Role} enum'una dönüştürülür.
 */
public record AssignRoleRequest(
        @NotBlank
        @Pattern(regexp = "USER", message = "Geçerli rol: USER (ADMIN atanamaz)")
        String role
) {
    public Role toDomainRole() {
        return Role.valueOf(role);
    }
}
