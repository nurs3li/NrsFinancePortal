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
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
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
import java.util.Optional;
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
    private final NotificationEventKafkaPublisher notificationEventKafkaPublisher;
    private static final Set<ReviewTaskStatus> FM_OPEN = Set.of(
            ReviewTaskStatus.PENDING, ReviewTaskStatus.IN_REVIEW, ReviewTaskStatus.ESCALATED
    );
    private static final Set<ReviewTaskStatus> ANY_OPEN = Set.of(
            ReviewTaskStatus.PENDING, ReviewTaskStatus.IN_REVIEW, ReviewTaskStatus.ESCALATED,
            ReviewTaskStatus.FREEZE_REQUESTED
    );
    private static final Set<ReviewTaskType> ADMIN_TASK_TYPES = Set.of(
            ReviewTaskType.FREEZE_APPROVAL
    );
    private Optional<Account> findAccountForUser(Long userId) {
        if (userId == null) return Optional.empty();
        List<Account> cashAccounts = accountRepository.findByUserIdAndType(userId, AccountType.CASH);
        if (!cashAccounts.isEmpty()) {
            return Optional.of(cashAccounts.get(0));
        }
        return userRepository.findById(userId)
                .flatMap(u -> {
                    List<Account> accounts = accountRepository.findByUser(u);
                    return accounts.isEmpty()
                            ? Optional.<Account>empty()
                            : Optional.of(accounts.get(0));
                });
    }
    private boolean hasOpenTask(Long userId) {
        return findAccountForUser(userId)
                .map(account -> reviewTaskRepository.existsByStatusInAndAccount_Id(
                        ANY_OPEN, account.getId()))
                .orElse(false);
    }
    @Transactional
    public void createFromSuspiciousEvent(Long suspiciousEventId) {
        SuspiciousEvent event = suspiciousEventRepository.findById(suspiciousEventId)
                .orElseThrow(() -> new IllegalStateException("Suspicious event not found: " + suspiciousEventId));
        Long userId = event.getUserId();
        if (hasOpenTask(userId)) {
            log.info("[TASK] Skipping SUSPICIOUS_REVIEW for userId={}, open task already exists for this account", userId);
            return;
        }
        ReviewTask task = ReviewTask.createSuspiciousReview(
                suspiciousEventId,
                null,
                Instant.now().plusSeconds(24 * 3600)
        );
        findAccountForUser(userId).ifPresent(task::setAccount);
        reviewTaskRepository.save(task);
        userRepository.findByRole(Role.FINANCE_MANAGER).forEach(fm ->
                notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                        fm.getKeycloakUserId(),
                        "Yeni inceleme görevi oluşturuldu",
                        "Şüpheli işlem (#" + suspiciousEventId + ") için yeni inceleme görevi atandı.",
                        "REVIEW_TASK_CREATED",
                        "review_task",
                        task.getId()
                ))
        );
        log.info("[TASK] Created SUSPICIOUS_REVIEW task id={} ref={} accountId={}",
                task.getId(), suspiciousEventId,
                task.getAccount() != null ? task.getAccount().getId() : null);
    }
    @Transactional
    public void createFromWhaleAlert(Long userId) {
        if (hasOpenTask(userId)) {
            log.info("[TASK] Skipping WHALE_REVIEW for userId={}, open task already exists for this account", userId);
            return;
        }
        ReviewTask task = ReviewTask.createWhaleReview(
                userId,
                Instant.now().plusSeconds(24 * 3600)
        );
        findAccountForUser(userId).ifPresent(task::setAccount);
        reviewTaskRepository.save(task);
        userRepository.findByRole(Role.FINANCE_MANAGER).forEach(fm ->
                notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                        fm.getKeycloakUserId(),
                        "Yeni whale inceleme görevi",
                        "Whale alert sonrası kullanıcı (#" + userId + ") için inceleme görevi oluşturuldu.",
                        "REVIEW_TASK_CREATED",
                        "review_task",
                        task.getId()
                ))
        );
        log.info("[TASK] Created WHALE_REVIEW task id={} userId={} accountId={}",
                task.getId(), userId,
                task.getAccount() != null ? task.getAccount().getId() : null);
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

            if (task.getAccount() != null) {
                notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                        task.getAccount().getUser().getKeycloakUserId(),
                        "İnceleme tamamlandı",
                        "Hesabınızdaki inceleme tamamlanmıştır. Herhangi bir sorun tespit edilmemiştir.",
                        "REVIEW_COMPLETED",
                        "review_task",
                        task.getId()
                ));
            }

            return ReviewTaskView.from(task);
        }

        if ("SUGGEST_FREEZE".equals(action)) {
            if (accountId == null) throw new IllegalArgumentException("accountId required for SUGGEST_FREEZE");
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new IllegalStateException("Account not found: " + accountId));

            boolean alreadyRequested = reviewTaskRepository.existsByStatusInAndAccount_Id(
                    Set.of(ReviewTaskStatus.FREEZE_REQUESTED), account.getId());
            if (alreadyRequested) {
                throw new IllegalStateException("Bu hesap için zaten bir freeze talebi mevcut.");
            }

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

            notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                    account.getUser().getKeycloakUserId(),
                    "Hesabınız donduruldu",
                    reason != null ? reason : "Hesabınız inceleme sonucu donduruldu.",
                    "ACCOUNT_FROZEN",
                    "account",
                    account.getId()
            ));
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
    public int cleanupDuplicateTasks() {
        AtomicInteger cleaned = new AtomicInteger(0);

        for (ReviewTaskType type : List.of(ReviewTaskType.SUSPICIOUS_REVIEW, ReviewTaskType.WHALE_REVIEW)) {
            List<ReviewTask> openTasks = reviewTaskRepository.findByTypeAndStatusIn(type, FM_OPEN);

            java.util.Map<Long, List<ReviewTask>> byAccount = openTasks.stream()
                    .filter(t -> t.getAccount() != null)
                    .collect(java.util.stream.Collectors.groupingBy(t -> t.getAccount().getId()));

            byAccount.forEach((acctId, tasks) -> {
                if (tasks.size() <= 1) return;
                tasks.sort(java.util.Comparator.comparing(ReviewTask::getCreatedAt).reversed());
                for (int i = 1; i < tasks.size(); i++) {
                    ReviewTask dup = tasks.get(i);
                    dup.setStatus(ReviewTaskStatus.REJECTED);
                    dup.setOutcome("Duplike — otomatik temizlendi");
                    dup.setCompletedAt(Instant.now());
                    reviewTaskRepository.save(dup);
                    cleaned.incrementAndGet();
                    log.info("[CLEANUP] Closed duplicate task id={} type={} accountId={}", dup.getId(), type, acctId);
                }
            });
        }

        return cleaned.get();
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