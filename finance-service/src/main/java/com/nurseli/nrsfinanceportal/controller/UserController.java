package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.UserResponse;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserResolver currentUserResolver;

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

    // GET /api/users/me
    @PreAuthorize("hasAnyRole('USER', 'FINANCE_MANAGER', 'ADMIN')")
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
