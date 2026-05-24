package com.nurseli.nrsfinanceportal.api.dto;

/**
 * Admin kullanıcı inceleme response'u; kimlik bilgileri ve dashboard özetini taşır.
 */
public record AdminUserInspectionResponse(
        Long userId,
        String username,
        String email,
        String role,
        DashboardSummaryResponse dashboardSummary
) {}
