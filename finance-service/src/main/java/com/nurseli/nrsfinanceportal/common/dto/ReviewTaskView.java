package com.nurseli.nrsfinanceportal.common.dto;

import com.nurseli.nrsfinanceportal.domain.task.ReviewTask;

import java.time.Instant;

public record ReviewTaskView(
        Long id,
        String type,
        Long referenceId,
        String assigneeRole,
        String status,
        String priority,
        Instant dueAt,
        Instant createdAt,
        String outcome,
        Long accountId,
        Long subjectUserId,
        String subjectUsername,
        String assignedFmKeycloakId,
        Instant claimedAt,
        /** POOL | MINE | OTHER — FM listesi için */
        String claimState,
        /** Salt okunur modda aksiyon engeli açıklaması (opsiyonel) */
        String readOnlyHint
) {
    public static ReviewTaskView from(ReviewTask t) {
        return from(t, null);
    }

    public static ReviewTaskView from(ReviewTask t, String currentKeycloakSub) {
        Long subUid = null;
        String subUn = null;
        if (t.getAccount() != null && t.getAccount().getUser() != null) {
            subUid = t.getAccount().getUser().getId();
            subUn = t.getAccount().getUser().getUsername();
        }
        String claimState = "POOL";
        if (t.getAssignedFmKeycloakId() != null && !t.getAssignedFmKeycloakId().isBlank()) {
            if (currentKeycloakSub != null && currentKeycloakSub.equals(t.getAssignedFmKeycloakId())) {
                claimState = "MINE";
            } else {
                claimState = "OTHER";
            }
        }
        String readOnlyHint = null;
        if ("FINANCE_MANAGER".equals(t.getAssigneeRole()) && currentKeycloakSub != null) {
            boolean claimedByOther = "OTHER".equals(claimState);
            boolean pendingUnclaimed = "POOL".equals(claimState);
            if (claimedByOther) {
                readOnlyHint = "Bu görev başka bir FM tarafından üstlenildi; salt okunur.";
            } else if (pendingUnclaimed) {
                readOnlyHint = "Önce görevi üzerinize alın.";
            }
        }
        return new ReviewTaskView(
                t.getId(),
                t.getType().name(),
                t.getReferenceId(),
                t.getAssigneeRole(),
                t.getStatus().name(),
                t.getPriority() != null ? t.getPriority().name() : "NORMAL",
                t.getDueAt(),
                t.getCreatedAt(),
                t.getOutcome(),
                t.getAccount() != null ? t.getAccount().getId() : null,
                subUid,
                subUn,
                t.getAssignedFmKeycloakId(),
                t.getClaimedAt(),
                claimState,
                readOnlyHint
        );
    }
}
