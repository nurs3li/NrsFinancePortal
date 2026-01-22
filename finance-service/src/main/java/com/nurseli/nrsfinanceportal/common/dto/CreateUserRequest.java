package com.nurseli.nrsfinanceportal.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class CreateUserRequest {

    @NotBlank(message = "Username boş olamaz")
    private String username;

    @NotBlank(message = "Email boş olamaz")
    @Email(message = "Geçerli bir email adresi giriniz")
    private String email;

    @NotBlank(message = "Role boş olamaz")
    private String role;

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }
}
