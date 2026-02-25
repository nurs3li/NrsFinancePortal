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
        Long accountId
) {
    public static ReviewTaskView from(ReviewTask t) {
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
                t.getAccount() != null ? t.getAccount().getId() : null
        );
    }
}