package com.nurseli.nrsfinanceportal.controller.admin;

import com.nurseli.nrsfinanceportal.observability.OpenSearchAuditLogService;
import com.nurseli.nrsfinanceportal.observability.dto.AuditLogDetailResponse;
import com.nurseli.nrsfinanceportal.observability.dto.AuditLogPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final OpenSearchAuditLogService openSearchAuditLogService;

    @GetMapping("/logs")
    public AuditLogPageResponse listLogs(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "serviceName", required = false) String serviceName,
            @RequestParam(name = "level", required = false) String level,
            /** Virgülle ayrılmış: WARN,ERROR,INFO — doluysa tek {@code level} yerine bunu kullanır */
            @RequestParam(name = "levels", required = false) String levels,
            @RequestParam(name = "traceId", required = false) String traceId,
            @RequestParam(name = "correlationId", required = false) String correlationId,
            @RequestParam(name = "userId", required = false) String userId,
            @RequestParam(name = "actionType", required = false) String actionType,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "q", required = false) String q
    ) {
        String fromIso = (from == null || from.isBlank()) ? Instant.now().minus(1, ChronoUnit.DAYS).toString() : from.trim();
        String toIso = (to == null || to.isBlank()) ? Instant.now().toString() : to.trim();
        String safeQ = q == null ? null : q.trim().length() > 200 ? q.trim().substring(0, 200) : q.trim();
        String safeUserId = userId == null ? null : userId.trim().length() > 64 ? userId.trim().substring(0, 64) : userId.trim();
        String safeAction = actionType == null ? null : actionType.trim().length() > 64 ? actionType.trim().substring(0, 64) : actionType.trim();
        String safeUsername = username == null ? null : username.trim().length() > 128 ? username.trim().substring(0, 128) : username.trim();
        return openSearchAuditLogService.search(
                fromIso, toIso, page, size, serviceName, level, levels, traceId, correlationId,
                blankToNull(safeUserId), blankToNull(safeAction), blankToNull(safeUsername), safeQ);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    @GetMapping("/logs/detail")
    public AuditLogDetailResponse logDetail(@RequestParam("cursor") String cursor) {
        return openSearchAuditLogService.getByCursor(cursor);
    }
}
