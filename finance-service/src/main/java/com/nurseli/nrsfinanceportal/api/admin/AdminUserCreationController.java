package com.nurseli.nrsfinanceportal.api.admin;

import com.nurseli.nrsfinanceportal.api.dto.CreateUserRequest;
import com.nurseli.nrsfinanceportal.api.dto.UserResponse;
import com.nurseli.nrsfinanceportal.api.response.ApiResponse;
import com.nurseli.nrsfinanceportal.application.admin.AdminUserCreationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin kullanıcı oluşturma endpoint'i (Keycloak Admin API).
 */
@RestController
@RequestMapping({"/api/v1/admin/users", "/api/admin/users"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserCreationController {

    private final AdminUserCreationService adminUserCreationService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        var user = adminUserCreationService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(UserResponse.from(user)));
    }
}
