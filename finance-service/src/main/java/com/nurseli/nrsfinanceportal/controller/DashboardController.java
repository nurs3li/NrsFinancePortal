package com.nurseli.nrsfinanceportal.controller;

import com.nurseli.nrsfinanceportal.common.dto.DashboardSummaryResponse;
import com.nurseli.nrsfinanceportal.service.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.service.DashboardSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardSummaryService dashboardSummaryService;
    private final CurrentUserResolver currentUserResolver;
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @GetMapping("/summary")

    public DashboardSummaryResponse summary() {

        Long userId = currentUserResolver
                .getOrCreateCurrentUser()
                .getId();

        return dashboardSummaryService.getSummary(userId);
    }
}
