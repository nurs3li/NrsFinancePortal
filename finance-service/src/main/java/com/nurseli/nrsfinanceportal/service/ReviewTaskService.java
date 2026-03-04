package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.ReviewTaskView;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.account.AccountStatus;
import com.nurseli.nrsfinanceportal.domain.suspicious.SuspiciousEvent;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTask;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskStatus;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskType;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.ReviewTaskRepository;
import com.nurseli.nrsfinanceportal.repository.SuspiciousEventRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewTaskService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final SuspiciousEventRepository suspiciousEventRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final CurrentUserResolver currentUserResolver;

    private static final Set<ReviewTaskStatus> FM_OPEN = Set.of(
            ReviewTaskStatus.PENDING, ReviewTaskStatus.IN_REVIEW, ReviewTaskStatus.ESCALATED
    );
    private static final Set<ReviewTaskType> ADMIN_TASK_TYPES = Set.of(
            ReviewTaskType.FREEZE_APPROVAL
    );
    /**
     * Kullanıcının bir hesabını döner: önce CASH, yoksa herhangi bir hesap.
     */
    // ReviewTaskService içinde
    /**
     * Kullanıcının bir hesabını döner: önce CASH, yoksa herhangi bir hesap.
     */
    private java.util.Optional<Account> findAccountForUser(Long userId) {
        if (userId == null) return java.util.Optional.empty();

        // Önce CASH hesaplardan birini al (birden fazlaysa ilkini kullan)
        List<Account> cashAccounts = accountRepository.findByUserIdAndType(userId, AccountType.CASH);
        if (!cashAccounts.isEmpty()) {
            return java.util.Optional.of(cashAccounts.get(0));
        }

        // CASH yoksa, kullanıcının herhangi bir hesabını al (ilkini kullan)
        return userRepository.findById(userId)
                .flatMap(u -> {
                    List<Account> accounts = accountRepository.findByUser(u);
                    return accounts.isEmpty()
                            ? java.util.Optional.<Account>empty()
                            : java.util.Optional.of(accounts.get(0));
                });
    }
    @Transactional
    public void createFromSuspiciousEvent(Long suspiciousEventId) {
        SuspiciousEvent event = suspiciousEventRepository.findById(suspiciousEventId)
                .orElseThrow(() -> new IllegalStateException("Suspicious event not found: " + suspiciousEventId));
        Long userId = event.getUserId();

        ReviewTask task = ReviewTask.createSuspiciousReview(
                suspiciousEventId,
                null,
                Instant.now().plusSeconds(24 * 3600)
        );
        findAccountForUser(userId).ifPresent(task::setAccount);
        reviewTaskRepository.save(task);
        log.info("[TASK] Created SUSPICIOUS_REVIEW task id={} ref={} accountId={}", task.getId(), suspiciousEventId, task.getAccount() != null ? task.getAccount().getId() : null);
    }

    @Transactional
    public void createFromWhaleAlert(Long userId) {
        ReviewTask task = ReviewTask.createWhaleReview(
                userId,
                Instant.now().plusSeconds(24 * 3600)
        );
        findAccountForUser(userId).ifPresent(task::setAccount);
        reviewTaskRepository.save(task);
        log.info("[TASK] Created WHALE_REVIEW task id={} userId={} accountId={}", task.getId(), userId, task.getAccount() != null ? task.getAccount().getId() : null);
    }
    @Transactional
    public int backfillAccountForExistingTasks() {
        List<ReviewTask> tasks = reviewTaskRepository.findAll().stream()
                .filter(t -> t.getAccount() == null && t.getReferenceId() != null)
                .toList();
        AtomicInteger updated = new AtomicInteger(0);
        for (ReviewTask task : tasks) {
            if (task.getType() == ReviewTaskType.SUSPICIOUS_REVIEW) {
                suspiciousEventRepository.findById(task.getReferenceId())
                        .map(SuspiciousEvent::getUserId)
                        .flatMap(this::findAccountForUser)
                        .ifPresent(acc -> {
                            task.setAccount(acc);
                            reviewTaskRepository.save(task);
                            updated.incrementAndGet();
                            log.info("[TASK] Backfilled accountId={} for task id={}", acc.getId(), task.getId());
                        });
            } else if (task.getType() == ReviewTaskType.WHALE_REVIEW) {
                findAccountForUser(task.getReferenceId())
                        .ifPresent(acc -> {
                            task.setAccount(acc);
                            reviewTaskRepository.save(task);
                            updated.incrementAndGet();
                            log.info("[TASK] Backfilled accountId={} for WHALE_REVIEW task id={}", acc.getId(), task.getId());
                        });
            }
        }
        return updated.get();
    }
    @Transactional(readOnly = true)
    public Page<ReviewTaskView> getMyTasks(Pageable pageable, ReviewTaskStatus statusFilter, ReviewTaskType typeFilter) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        String role = user.getRole().name();
        if (!"FINANCE_MANAGER".equals(role) && !"ADMIN".equals(role)) {
            return Page.empty(pageable);
        }
        if (statusFilter != null) {
            List<ReviewTaskStatus> statuses = statusFilter == ReviewTaskStatus.PENDING
                    ? List.of(ReviewTaskStatus.PENDING, ReviewTaskStatus.IN_REVIEW)
                    : statusFilter == ReviewTaskStatus.ESCALATED
                    ? List.of(ReviewTaskStatus.ESCALATED)
                    : List.of(statusFilter);
            return reviewTaskRepository
                    .findByAssigneeRoleAndStatusInOrderByDueAtAsc(role, statuses, pageable)
                    .map(ReviewTaskView::from);
        }
        if (typeFilter != null && "ADMIN".equals(role)) {
            return reviewTaskRepository
                    .findByAssigneeRoleAndTypeInOrderByCreatedAtDesc(role, List.of(typeFilter), pageable)
                    .map(ReviewTaskView::from);
        }
        return reviewTaskRepository
                .findByAssigneeRoleOrderByCreatedAtDesc(role, pageable)
                .map(ReviewTaskView::from);
    }

    @Transactional(readOnly = true)
    public ReviewTask getById(Long id) {
        return reviewTaskRepository.findByIdWithAccount(id)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + id));
    }

    @Transactional
    public ReviewTaskView executeAction(Long taskId, String action, Long accountId) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ReviewTask task = getById(taskId);
        if (!FM_OPEN.contains(task.getStatus()) && !ReviewTaskStatus.FREEZE_REQUESTED.equals(task.getStatus())) {
            throw new IllegalStateException("Task not in actionable state");
        }
        if ("APPROVE".equals(action)) {
            task.setStatus(ReviewTaskStatus.APPROVED);
            task.setOutcome("Temiz");
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);
            return ReviewTaskView.from(task);
        }
        if ("REJECT".equals(action)) {
            task.setStatus(ReviewTaskStatus.REJECTED);
            task.setOutcome("Reddet");
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);
            return ReviewTaskView.from(task);
        }
        if ("TAKE_UNDER_MONITORING".equals(action)) {
            task.setStatus(ReviewTaskStatus.APPROVED);
            task.setOutcome("Takibe al");
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);
            return ReviewTaskView.from(task);
        }
        if ("SUGGEST_FREEZE".equals(action)) {
            if (accountId == null) throw new IllegalArgumentException("accountId required for SUGGEST_FREEZE");
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new IllegalStateException("Account not found: " + accountId));
            task.setStatus(ReviewTaskStatus.FREEZE_REQUESTED);
            task.setOutcome("Freeze öner");
            task.setAccount(account);
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);
            ReviewTask adminTask = ReviewTask.createFreezeApproval(
                    accountId, user.getId(), Instant.now().plusSeconds(24 * 3600)
            );
            adminTask.setAccount(account);
            reviewTaskRepository.save(adminTask);
            log.info("[TASK] Freeze requested for account {} -> ADMIN task id={}", accountId, adminTask.getId());
            return ReviewTaskView.from(task);
        }
        throw new IllegalArgumentException("Unknown action: " + action);
    }

    @Transactional(readOnly = true)
    public Page<ReviewTaskView> getAdminTasks(Pageable pageable, ReviewTaskStatus statusFilter) {
        List<ReviewTaskStatus> statuses = statusFilter != null
                ? List.of(statusFilter)
                : List.of(ReviewTaskStatus.PENDING, ReviewTaskStatus.ESCALATED);
        return reviewTaskRepository
                .findByAssigneeRoleAndStatusInOrderByDueAtAsc("ADMIN", statuses, pageable)
                .map(ReviewTaskView::from);
    }

    @Transactional
    public ReviewTaskView executeAdminAction(Long taskId, String action, String reason) {
        ReviewTask task = getById(taskId);
        if (!"ADMIN".equals(task.getAssigneeRole())) {
            throw new IllegalStateException("Not an admin task");
        }
        if ("FREEZE".equals(action)) {
            Account account = task.getAccount();
            if (account == null) throw new IllegalStateException("No account on task");
            account.freeze(Instant.now(), reason != null ? reason : "Admin freeze");
            accountRepository.save(account);
            task.setStatus(ReviewTaskStatus.APPROVED);
            task.setOutcome("Hesap dondur");
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);
            return ReviewTaskView.from(task);
        }
        if ("REJECT_FREEZE".equals(action)) {
            task.setStatus(ReviewTaskStatus.REJECTED);
            task.setOutcome("Reddet");
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);
            return ReviewTaskView.from(task);
        }
        throw new IllegalArgumentException("Unknown admin action: " + action);
    }

    @Transactional
    public void escalateOverdueTasks() {
        List<ReviewTask> overdue = reviewTaskRepository.findByStatusAndDueAtBefore(
                ReviewTaskStatus.PENDING, Instant.now());
        for (ReviewTask t : overdue) {
            if (!"FINANCE_MANAGER".equals(t.getAssigneeRole())) continue;
            t.setStatus(ReviewTaskStatus.ESCALATED);
            t.setAssigneeRole("ADMIN");
            t.setSlaEscalatedAt(Instant.now());
            reviewTaskRepository.save(t);
            log.info("[TASK] Escalated task id={} to ADMIN", t.getId());
        }
    }
}