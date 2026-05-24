package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.UserResponse;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.application.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.application.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Kullanıcı listeleme ve oturum açmış kullanıcı bilgisi endpoint'lerini sunar.
 */
@RestController
@RequestMapping({"/api/v1/users", "/api/users"})
public class UserController {

    private final UserService userService;
    private final CurrentUserResolver currentUserResolver;

    /**
     * {@code UserController} — Kullanıcı servisi ve oturum çözümleyici ile oluşturulur.
     */
    public UserController(UserService userService,
                          CurrentUserResolver currentUserResolver) {
        this.userService = userService;
        this.currentUserResolver = currentUserResolver;
    }

    // GET /api/users
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")

    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsers() {
        return ResponseEntity.ok(
                ApiResponse.success(userService.getAllUsers())
        );
    }

    /**
     * {@code getMe} — JWT'den çözümlenen mevcut kullanıcının profil DTO'sunu döner.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/me")

    public ResponseEntity<ApiResponse<UserResponse>> getMe() {
        return ResponseEntity.ok(
                ApiResponse.success(
                        UserResponse.from(
                                currentUserResolver.getOrCreateCurrentUser()
                        )
                )
        );
    }
}
