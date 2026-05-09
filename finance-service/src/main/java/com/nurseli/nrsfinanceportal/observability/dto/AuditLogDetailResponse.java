package com.nurseli.nrsfinanceportal.observability.dto;

import java.util.Map;

public record AuditLogDetailResponse(
        boolean enabled,
        String disabledReason,
        Map<String, Object> source
) {
    public static AuditLogDetailResponse disabled(String reason) {
        return new AuditLogDetailResponse(false, reason, Map.of());
    }
}
