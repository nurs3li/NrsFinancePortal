package com.nurseli.nrsfinanceportal.observability.dto;

import java.util.Collections;
import java.util.List;

public record AuditLogPageResponse(
        boolean enabled,
        String disabledReason,
        List<AuditLogRowDto> items,
        long total,
        int page,
        int size
) {
    public static AuditLogPageResponse disabled(String reason) {
        return new AuditLogPageResponse(false, reason, Collections.emptyList(), 0, 0, 0);
    }
}
