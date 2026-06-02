package com.nurseli.nrsfinanceportal.api;

import com.nurseli.nrsfinanceportal.api.dto.DashboardSummaryResponse;
import com.nurseli.nrsfinanceportal.application.user.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.application.dashboard.DashboardSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Oturum açmış kullanıcı için dashboard özet endpoint'lerini sunar.
 */
@RestController
@RequestMapping({"/api/v1/dashboard", "/api/dashboard"})
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardSummaryService dashboardSummaryService;
    private final CurrentUserResolver currentUserResolver;

    /**
     * {@code summary} — Giqriş yapmış kullanıcının portfolio ve piyasa özet DTO'sunu döner.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/summary")

    public DashboardSummaryResponse summary() {

        Long userId = currentUserResolver
                .getOrCreateCurrentUser()
                .getId();

        return dashboardSummaryService.getSummary(userId);
    }
}
