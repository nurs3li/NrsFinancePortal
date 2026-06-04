package com.nurseli.nrsfinanceportal.api.dto;

import com.nurseli.nrsfinanceportal.domain.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Admin tarafından yeni kullanıcı oluşturma request'i.
 */
public class CreateUserRequest {

    @NotBlank(message = "Username boş olamaz")
    private String username;

    @NotBlank(message = "Email boş olamaz")
    @Email(message = "Geçerli bir email adresi giriniz")
    private String email;

    @NotBlank(message = "Şifre boş olamaz")
    @Size(min = 8, message = "Şifre en az 8 karakter olmalı")
    private String password;

    private String firstName;

    private String lastName;

    @NotBlank(message = "Role boş olamaz")
    @Pattern(regexp = "USER", message = "Geçerli rol: USER (ADMIN atanamaz)")
    private String role;

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getRole() {
        return role;
    }

    public Role toDomainRole() {
        return Role.valueOf(role);
    }
}
