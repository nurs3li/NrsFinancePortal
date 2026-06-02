package com.nurseli.nrsfinanceportal.api.admin;

import com.nurseli.nrsfinanceportal.api.dto.AdminUserInspectionResponse;
import com.nurseli.nrsfinanceportal.api.dto.DashboardSummaryResponse;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import com.nurseli.nrsfinanceportal.application.dashboard.DashboardSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin kullanıcı inceleme ekranı için profil ve dashboard özet endpoint'lerini sunar.
 */
@RestController
@RequestMapping({"/api/v1/admin/users", "/api/admin/users"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserInspectionController {

    private final UserRepository userRepository;
    private final DashboardSummaryService dashboardSummaryService;

    /**
     * {@code inspection} — Kullanıcı kimliği ile profil ve (USER rolünde) dashboard özet DTO'sunu döner.
     */
    @GetMapping("/{userId}/inspection")
    public AdminUserInspectionResponse inspection(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));

        DashboardSummaryResponse dashboardSummary = null;
        if (user.getRole() == Role.USER) {
            dashboardSummary = dashboardSummaryService.getSummary(userId);
        }

        return new AdminUserInspectionResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                dashboardSummary
        );
    }
}
