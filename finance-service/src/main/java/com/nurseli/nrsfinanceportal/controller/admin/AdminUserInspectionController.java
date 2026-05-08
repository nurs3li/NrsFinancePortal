package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.common.dto.AdminUserInspectionResponse;
import com.nurseli.nrsfinanceportal.common.dto.DashboardSummaryResponse;
import com.nurseli.nrsfinanceportal.common.dto.FmTaskSummaryDto;
import com.nurseli.nrsfinanceportal.common.dto.RiskMonitorUserDetailResponse;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import com.nurseli.nrsfinanceportal.service.DashboardSummaryService;
import com.nurseli.nrsfinanceportal.service.ReviewTaskService;
import com.nurseli.nrsfinanceportal.service.WhaleTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserInspectionController {

    private final UserRepository userRepository;
    private final DashboardSummaryService dashboardSummaryService;
    private final WhaleTimelineService whaleTimelineService;
    private final ReviewTaskService reviewTaskService;

    @GetMapping("/{userId}/inspection")
    public AdminUserInspectionResponse inspection(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId));

        DashboardSummaryResponse dashboardSummary = null;
        RiskMonitorUserDetailResponse riskMonitorDetail = null;
        FmTaskSummaryDto fmTaskSummary = null;

        if (user.getRole() == Role.USER) {
            dashboardSummary = dashboardSummaryService.getSummary(userId);
            riskMonitorDetail = whaleTimelineService.getRiskMonitorUserDetail(userId);
        } else if (user.getRole() == Role.FINANCE_MANAGER) {
            fmTaskSummary = reviewTaskService.getFmTaskSummaryForManager(user);
        }

        return new AdminUserInspectionResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                dashboardSummary,
                riskMonitorDetail,
                fmTaskSummary
        );
    }
}
