package com.nurseli.nrsfinanceportal.common.dto;

public record AdminUserInspectionResponse(
        Long userId,
        String username,
        String email,
        String role,
        DashboardSummaryResponse dashboardSummary,
        RiskMonitorUserDetailResponse riskMonitorDetail,
        FmTaskSummaryDto fmTaskSummary
) {}
