package com.nurseli.nrsfinanceportal.infrastructure.observability.dto;

import java.util.Map;

/**
 * Tek audit log kaydı detay yanıtı.
 */
public record AuditLogDetailResponse(
        boolean enabled,
        String disabledReason,
        Map<String, Object> source
) {
    public static AuditLogDetailResponse disabled(String reason) {
        return new AuditLogDetailResponse(false, reason, Map.of());
    }
}
