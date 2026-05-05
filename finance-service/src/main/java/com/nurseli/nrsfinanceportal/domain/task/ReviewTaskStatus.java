package com.nurseli.nrsfinanceportal.domain.task;

public enum ReviewTaskStatus {
    PENDING,
    /** FM görev havuzunda üzerine alınmış */
    CLAIMED,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    ESCALATED,
    FREEZE_REQUESTED
}