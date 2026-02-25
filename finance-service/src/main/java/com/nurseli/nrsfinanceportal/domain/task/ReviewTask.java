package com.nurseli.nrsfinanceportal.domain.task;

import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "review_tasks")
public class ReviewTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private ReviewTaskType type;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "assignee_role", nullable = false, length = 50)
    private String assigneeRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReviewTaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    private ReviewTaskPriority priority = ReviewTaskPriority.NORMAL;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "outcome", length = 100)
    private String outcome;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(name = "sla_escalated_at")
    private Instant slaEscalatedAt;

    protected ReviewTask() {}

    public Long getId() { return id; }
    public ReviewTaskType getType() { return type; }
    public Long getReferenceId() { return referenceId; }
    public String getAssigneeRole() { return assigneeRole; }
    public ReviewTaskStatus getStatus() { return status; }
    public ReviewTaskPriority getPriority() { return priority; }
    public Instant getDueAt() { return dueAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getOutcome() { return outcome; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Account getAccount() { return account; }
    public Instant getSlaEscalatedAt() { return slaEscalatedAt; }

    public void setStatus(ReviewTaskStatus status) { this.status = status; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public void setAccount(Account account) { this.account = account; }
    public void setAssigneeRole(String assigneeRole) { this.assigneeRole = assigneeRole; }
    public void setSlaEscalatedAt(Instant slaEscalatedAt) { this.slaEscalatedAt = slaEscalatedAt; }

    public static ReviewTask createSuspiciousReview(Long suspiciousEventId, Long userId, Instant dueAt) {
        ReviewTask t = new ReviewTask();
        t.type = ReviewTaskType.SUSPICIOUS_REVIEW;
        t.referenceId = suspiciousEventId;
        t.assigneeRole = "FINANCE_MANAGER";
        t.status = ReviewTaskStatus.PENDING;
        t.priority = ReviewTaskPriority.NORMAL;
        t.dueAt = dueAt;
        t.createdAt = Instant.now();
        return t;
    }

    public static ReviewTask createWhaleReview(Long userId, Instant dueAt) {
        ReviewTask t = new ReviewTask();
        t.type = ReviewTaskType.WHALE_REVIEW;
        t.referenceId = userId;
        t.assigneeRole = "FINANCE_MANAGER";
        t.status = ReviewTaskStatus.PENDING;
        t.priority = ReviewTaskPriority.HIGH;
        t.dueAt = dueAt;
        t.createdAt = Instant.now();
        return t;
    }

    public static ReviewTask createFreezeApproval(Long accountId, Long createdByUserId, Instant dueAt) {
        ReviewTask t = new ReviewTask();
        t.type = ReviewTaskType.FREEZE_APPROVAL;
        t.assigneeRole = "ADMIN";
        t.status = ReviewTaskStatus.PENDING;
        t.priority = ReviewTaskPriority.HIGH;
        t.referenceId = accountId;
        t.createdByUserId = createdByUserId;
        t.dueAt = dueAt;
        t.createdAt = Instant.now();
        return t;
    }
}