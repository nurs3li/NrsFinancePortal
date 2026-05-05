package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.common.dto.AssignRoleRequest;
import com.nurseli.nrsfinanceportal.common.response.ApiResponse;
import com.nurseli.nrsfinanceportal.service.UserRealmRoleAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserRoleController {

    private final UserRealmRoleAssignmentService userRealmRoleAssignmentService;

    @PostMapping("/{userId}/assign-role")
    public ResponseEntity<ApiResponse<String>> assignRole(
            @PathVariable Long userId,
            @Valid @RequestBody AssignRoleRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        userRealmRoleAssignmentService.assignRealmRole(userId, request.toDomainRole(), jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("OK"));
    }
}
