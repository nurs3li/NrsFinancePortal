package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.FmTaskSummaryDto;
import com.nurseli.nrsfinanceportal.common.dto.FmUserOptionDto;
import com.nurseli.nrsfinanceportal.common.dto.ReviewTaskView;
import com.nurseli.nrsfinanceportal.common.identity.JwtIdentityReader;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.suspicious.SuspiciousEvent;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTask;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskStatus;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskType;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.integration.kafka.NotificationEventKafkaPublisher;
import com.nurseli.nrsfinanceportal.integration.kafka.event.NotificationRequestedEvent;
import com.nurseli.nrsfinanceportal.integration.sse.TaskPoolSseService;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.ReviewTaskRepository;
import com.nurseli.nrsfinanceportal.repository.SuspiciousEventRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final JwtIdentityReader jwtIdentityReader;
    private final NotificationEventKafkaPublisher notificationEventKafkaPublisher;
    private final TaskPoolSseService taskPoolSseService;

    private static final Set<ReviewTaskStatus> FM_OPEN = Set.of(
            ReviewTaskStatus.PENDING,
            ReviewTaskStatus.IN_REVIEW,
            ReviewTaskStatus.CLAIMED,
            ReviewTaskStatus.ESCALATED
    );
    /** Duplike temizlikte eskale admin görevleri hariç */
    private static final Set<ReviewTaskStatus> FM_ACTIVE_FOR_DUP_CLEANUP = Set.of(
            ReviewTaskStatus.PENDING,
            ReviewTaskStatus.IN_REVIEW,
            ReviewTaskStatus.CLAIMED
    );
    private static final Set<ReviewTaskStatus> ANY_OPEN = Set.of(
            ReviewTaskStatus.PENDING,
            ReviewTaskStatus.IN_REVIEW,
            ReviewTaskStatus.CLAIMED,
            ReviewTaskStatus.ESCALATED,
            ReviewTaskStatus.FREEZE_REQUESTED
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

    /**
     * @return yeni inceleme görevi oluşturulduysa true; zaten açık görev varsa false (kullanıcı bildirimi tekrarlanmamalı)
     */
    @Transactional
    public boolean createFromSuspiciousEvent(Long suspiciousEventId) {
        SuspiciousEvent event = suspiciousEventRepository.findById(suspiciousEventId)
                .orElseThrow(() -> new IllegalStateException("Suspicious event not found: " + suspiciousEventId));
        Long userId = event.getUserId();
        if (hasOpenTask(userId)) {
            log.info("[TASK] Skipping SUSPICIOUS_REVIEW for userId={}, open task already exists for this account", userId);
            return false;
        }
        ReviewTask task = ReviewTask.createSuspiciousReview(
                suspiciousEventId,
                null,
                Instant.now().plusSeconds(24 * 3600)
        );
        findAccountForUser(userId).ifPresent(task::setAccount);
        reviewTaskRepository.save(task);
        SuspiciousEvent se = suspiciousEventRepository.findById(suspiciousEventId).orElse(null);
        User subjectUser = userRepository.findById(userId).orElse(null);
        String fmTitle = "Yeni inceleme görevi (havuz) — #" + task.getId();
        String fmBody = buildSuspiciousPoolTaskEmailBody(task, se, subjectUser);
        userRepository.findByRole(Role.FINANCE_MANAGER).forEach(fm ->
                notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                        fm.getKeycloakUserId(),
                        fmTitle,
                        fmBody,
                        "REVIEW_TASK_CREATED",
                        "review_task",
                        task.getId()
                ))
        );
        log.info("[TASK] Created SUSPICIOUS_REVIEW task id={} ref={} accountId={}",
                task.getId(), suspiciousEventId,
                task.getAccount() != null ? task.getAccount().getId() : null);
        return true;
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
        User whaleUser = userRepository.findById(userId).orElse(null);
        String fmTitleWhale = "Yeni inceleme görevi (havuz, öncelikli) — #" + task.getId();
        String fmBodyWhale = buildWhalePoolTaskEmailBody(task, whaleUser);
        userRepository.findByRole(Role.FINANCE_MANAGER).forEach(fm ->
                notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                        fm.getKeycloakUserId(),
                        fmTitleWhale,
                        fmBodyWhale,
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
    public Page<ReviewTaskView> getFmTaskPool(Pageable pageable) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        if (user.getRole() != Role.FINANCE_MANAGER) {
            return Page.empty(pageable);
        }
        String sub = jwtIdentityReader.getRequiredSubject();
        return reviewTaskRepository.findFmPoolUnclaimed(pageable)
                .map(t -> ReviewTaskView.from(t, sub));
    }

    @Transactional(readOnly = true)
    public FmTaskSummaryDto getFmTaskSummary() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        if (user.getRole() != Role.FINANCE_MANAGER) {
            return new FmTaskSummaryDto(0, 0, 0);
        }
        String sub = jwtIdentityReader.getRequiredSubject();
        long pool = reviewTaskRepository.countByAssigneeRoleAndStatusAndAssignedFmKeycloakIdIsNull(
                "FINANCE_MANAGER", ReviewTaskStatus.PENDING);
        long mineOpen = reviewTaskRepository.countByAssigneeRoleAndAssignedFmKeycloakIdAndStatus(
                "FINANCE_MANAGER", sub, ReviewTaskStatus.CLAIMED);
        long mineDone = reviewTaskRepository.countByAssigneeRoleAndAssignedFmKeycloakIdAndStatusIn(
                "FINANCE_MANAGER", sub,
                List.of(ReviewTaskStatus.APPROVED, ReviewTaskStatus.REJECTED, ReviewTaskStatus.FREEZE_REQUESTED));
        return new FmTaskSummaryDto(pool, mineOpen, mineDone);
    }

    @Transactional(readOnly = true)
    public FmTaskSummaryDto getFmTaskSummaryForManager(User manager) {
        if (manager == null || manager.getRole() != Role.FINANCE_MANAGER || manager.getKeycloakUserId() == null) {
            return new FmTaskSummaryDto(0, 0, 0);
        }
        String sub = manager.getKeycloakUserId();
        long pool = reviewTaskRepository.countByAssigneeRoleAndStatusAndAssignedFmKeycloakIdIsNull(
                "FINANCE_MANAGER", ReviewTaskStatus.PENDING);
        long mineOpen = reviewTaskRepository.countByAssigneeRoleAndAssignedFmKeycloakIdAndStatus(
                "FINANCE_MANAGER", sub, ReviewTaskStatus.CLAIMED);
        long mineDone = reviewTaskRepository.countByAssigneeRoleAndAssignedFmKeycloakIdAndStatusIn(
                "FINANCE_MANAGER", sub,
                List.of(ReviewTaskStatus.APPROVED, ReviewTaskStatus.REJECTED, ReviewTaskStatus.FREEZE_REQUESTED));
        return new FmTaskSummaryDto(pool, mineOpen, mineDone);
    }

    @Transactional(readOnly = true)
    public List<FmUserOptionDto> listFinanceManagersForAdmin() {
        return userRepository.findByRole(Role.FINANCE_MANAGER).stream()
                .map(FmUserOptionDto::from)
                .toList();
    }

    @Transactional
    public ReviewTaskView claimTask(Long taskId) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        if (user.getRole() != Role.FINANCE_MANAGER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sadece FM görev üstlenebilir");
        }
        String sub = jwtIdentityReader.getRequiredSubject();
        Instant now = Instant.now();
        int updated = reviewTaskRepository.tryClaim(
                taskId,
                sub,
                now,
                ReviewTaskStatus.CLAIMED,
                List.of(ReviewTaskStatus.PENDING, ReviewTaskStatus.IN_REVIEW));
        if (updated == 0) {
            ReviewTask t = reviewTaskRepository.findById(taskId)
                    .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));
            if (!"FINANCE_MANAGER".equals(t.getAssigneeRole())) {
                throw new IllegalStateException("Bu görev FM havuzunda değil");
            }
            if (t.getStatus() == ReviewTaskStatus.CLAIMED && sub.equals(t.getAssignedFmKeycloakId())) {
                ReviewTask fresh = reviewTaskRepository.findByIdWithAccountAndUser(taskId).orElse(t);
                return ReviewTaskView.from(fresh, sub);
            }
            if (t.getStatus() == ReviewTaskStatus.CLAIMED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu görev başka bir FM tarafından üstlenildi");
            }
            throw new IllegalStateException("Görev üstlenilemiyor (durum: " + t.getStatus() + ")");
        }
        ReviewTask fresh = reviewTaskRepository.findByIdWithAccountAndUser(taskId)
                .orElseGet(() -> reviewTaskRepository.findById(taskId).orElseThrow());
        long secondsToClaim = fresh.getClaimedAt() != null && fresh.getCreatedAt() != null
                ? ChronoUnit.SECONDS.between(fresh.getCreatedAt(), fresh.getClaimedAt())
                : -1;
        log.info("[AUDIT][TASK_CLAIMED] taskId={} fmSub={} secondsSinceCreated={}", taskId, sub, secondsToClaim);
        taskPoolSseService.broadcastFm(new HashMap<>(Map.of(
                "type", "TASK_CLAIMED",
                "taskId", taskId,
                "claimedBy", sub)));
        return ReviewTaskView.from(fresh, sub);
    }

    @Transactional(readOnly = true)
    public Page<ReviewTaskView> getMyTasks(Pageable pageable, ReviewTaskStatus statusFilter, ReviewTaskType typeFilter) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        String role = user.getRole().name();
        if (!"FINANCE_MANAGER".equals(role) && !"ADMIN".equals(role)) {
            return Page.empty(pageable);
        }
        String sub = "FINANCE_MANAGER".equals(role) ? jwtIdentityReader.getRequiredSubject() : null;
        if (statusFilter != null) {
            List<ReviewTaskStatus> statuses = statusFilter == ReviewTaskStatus.PENDING
                    ? List.of(ReviewTaskStatus.PENDING, ReviewTaskStatus.IN_REVIEW)
                    : statusFilter == ReviewTaskStatus.ESCALATED
                    ? List.of(ReviewTaskStatus.ESCALATED)
                    : List.of(statusFilter);
            return reviewTaskRepository
                    .findByAssigneeRoleAndStatusInOrderByDueAtAsc(role, statuses, pageable)
                    .map(t -> ReviewTaskView.from(t, sub));
        }
        if (typeFilter != null && "ADMIN".equals(role)) {
            return reviewTaskRepository
                    .findByAssigneeRoleAndTypeInOrderByCreatedAtDesc(role, List.of(typeFilter), pageable)
                    .map(t -> ReviewTaskView.from(t, sub));
        }
        return reviewTaskRepository
                .findByAssigneeRoleOrderByCreatedAtDesc(role, pageable)
                .map(t -> ReviewTaskView.from(t, sub));
    }

    @Transactional(readOnly = true)
    public ReviewTask getById(Long id) {
        return reviewTaskRepository.findByIdWithAccountAndUser(id)
                .orElseGet(() -> reviewTaskRepository.findByIdWithAccount(id)
                        .orElseThrow(() -> new IllegalStateException("Task not found: " + id)));
    }

    @Transactional(readOnly = true)
    public ReviewTaskView getTaskViewForCurrentUser(Long id) {
        ReviewTask t = getById(id);
        User u = currentUserResolver.getOrCreateCurrentUser();
        String sub = u.getRole() == Role.FINANCE_MANAGER ? jwtIdentityReader.getRequiredSubject() : null;
        return ReviewTaskView.from(t, sub);
    }

    private void assertFmInvestigationActions(ReviewTask task) {
        if (!"FINANCE_MANAGER".equals(task.getAssigneeRole())) {
            return;
        }
        if (task.getType() != ReviewTaskType.SUSPICIOUS_REVIEW && task.getType() != ReviewTaskType.WHALE_REVIEW) {
            return;
        }
        String sub = jwtIdentityReader.getRequiredSubject();
        if (task.getStatus() == ReviewTaskStatus.PENDING
                && (task.getAssignedFmKeycloakId() == null || task.getAssignedFmKeycloakId().isBlank())) {
            throw new IllegalStateException("Önce görevi üzerinize alın.");
        }
        if (task.getStatus() == ReviewTaskStatus.CLAIMED || task.getStatus() == ReviewTaskStatus.IN_REVIEW) {
            if (task.getAssignedFmKeycloakId() == null || !task.getAssignedFmKeycloakId().equals(sub)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu görevde işlem yapma yetkiniz yok");
            }
        }
    }

    @Transactional
    public ReviewTaskView executeAction(Long taskId, String action, Long accountId) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        ReviewTask task = getById(taskId);

        if (!FM_OPEN.contains(task.getStatus()) && !ReviewTaskStatus.FREEZE_REQUESTED.equals(task.getStatus())) {
            throw new IllegalStateException("Task not in actionable state");
        }

        if ("APPROVE".equals(action)) {
            assertFmInvestigationActions(task);
            task.setStatus(ReviewTaskStatus.APPROVED);
            task.setOutcome("Temiz");
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);
            logCompletionAudit(task, user, "APPROVE");

            if (task.getAccount() != null && task.getAccount().getUser() != null) {
                User endUser = task.getAccount().getUser();
                notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                        endUser.getKeycloakUserId(),
                        "İncelemeniz tamamlandı",
                        """
                                Merhaba,

                                Hesabınız üzerindeki inceleme tamamlanmış ve herhangi bir sakınca tespit edilmemiştir.

                                İyi günler dileriz,
                                NRS Finance Portal
                                """,
                        "REVIEW_COMPLETED",
                        "review_task",
                        task.getId()
                ));
            }

            return viewAfterMutation(taskId);
        }

        if ("SUGGEST_FREEZE".equals(action)) {
            assertFmInvestigationActions(task);
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
            logCompletionAudit(task, user, "SUGGEST_FREEZE");

            ReviewTask adminTask = ReviewTask.createFreezeApproval(
                    accountId, user.getId(), Instant.now().plusSeconds(24 * 3600)
            );
            adminTask.setAccount(account);
            reviewTaskRepository.save(adminTask);
            log.info("[TASK] Freeze requested for account {} -> ADMIN task id={}", accountId, adminTask.getId());

            String fmEmail = nvlText(user.getEmail(), user.getUsername());
            Long uid = account.getUser() != null ? account.getUser().getId() : null;
            String userEmail = account.getUser() != null ? nvlText(account.getUser().getEmail(), "—") : "—";
            String freezeBody = """
                    Merhaba,

                    Bir Finans Yöneticisi hesap dondurma talebinde bulundu.

                    Talep eden FM: %s
                    İlgili hesap No: %d
                    Kullanıcı ID: %s
                    Kullanıcı e-posta: %s
                    Admin görev ID: %d

                    Lütfen Admin panelinden talebi inceleyerek onay veya red aksiyonu alınız.

                    İyi çalışmalar,
                    NRS Finance Portal
                    """.formatted(fmEmail, accountId, uid != null ? uid.toString() : "—", userEmail, adminTask.getId());

            userRepository.findByRole(Role.ADMIN).forEach(admin ->
                    notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                            admin.getKeycloakUserId(),
                            "Hesap dondurma — Admin onay görevi #" + adminTask.getId(),
                            freezeBody,
                            "FREEZE_APPROVAL_CREATED",
                            "review_task",
                            adminTask.getId()
                    ))
            );

            return viewAfterMutation(taskId);
        }

        throw new IllegalArgumentException("Unknown action: " + action);
    }

    private void logCompletionAudit(ReviewTask task, User actor, String action) {
        Instant claimed = task.getClaimedAt();
        Instant done = task.getCompletedAt();
        long handleSec = claimed != null && done != null ? ChronoUnit.SECONDS.between(claimed, done) : -1;
        log.info("[AUDIT][TASK_RESOLVED] taskId={} action={} actorUserId={} handleSecondsSinceClaim={}",
                task.getId(), action, actor.getId(), handleSec);
    }

    private ReviewTaskView viewAfterMutation(Long taskId) {
        String sub = jwtIdentityReader.getRequiredSubject();
        ReviewTask fresh = reviewTaskRepository.findByIdWithAccountAndUser(taskId).orElseGet(() -> getById(taskId));
        return ReviewTaskView.from(fresh, sub);
    }

    @Transactional(readOnly = true)
    public Page<ReviewTaskView> getAdminTasks(Pageable pageable, ReviewTaskStatus statusFilter) {
        List<ReviewTaskStatus> statuses = statusFilter != null
                ? List.of(statusFilter)
                : List.of(ReviewTaskStatus.PENDING, ReviewTaskStatus.ESCALATED, ReviewTaskStatus.FREEZE_REQUESTED);
        return reviewTaskRepository
                .findAdminTasksEscalatedFirst(statuses, pageable)
                .map(ReviewTaskView::from);
    }

    @Transactional
    public ReviewTaskView executeAdminAction(Long taskId, String action, String reason) {
        return executeAdminTask(taskId, Map.of("action", action, "reason", reason != null ? reason : ""));
    }

    @Transactional
    public ReviewTaskView executeAdminTask(Long taskId, Map<String, String> body) {
        String action = body.get("action");
        if (action == null) {
            throw new IllegalArgumentException("action gerekli");
        }
        String reason = Optional.ofNullable(body.get("reason")).orElse("");
        ReviewTask task = getById(taskId);
        if (!"ADMIN".equals(task.getAssigneeRole())) {
            throw new IllegalStateException("Not an admin task");
        }

        if ("FORCE_ASSIGN_FM".equals(action)) {
            String fmUserIdRaw = body.get("fmUserId");
            if (fmUserIdRaw == null) {
                throw new IllegalArgumentException("fmUserId gerekli");
            }
            long fmUserId = Long.parseLong(fmUserIdRaw);
            User fm = userRepository.findById(fmUserId)
                    .orElseThrow(() -> new IllegalStateException("FM kullanıcı bulunamadı: " + fmUserId));
            if (fm.getRole() != Role.FINANCE_MANAGER) {
                throw new IllegalStateException("Seçilen kullanıcı FM değil");
            }
            if (task.getType() != ReviewTaskType.SUSPICIOUS_REVIEW && task.getType() != ReviewTaskType.WHALE_REVIEW) {
                throw new IllegalStateException("Zorla atama yalnızca inceleme görevleri için geçerlidir");
            }
            task.setAssigneeRole("FINANCE_MANAGER");
            task.setStatus(ReviewTaskStatus.CLAIMED);
            task.setAssignedFmKeycloakId(fm.getKeycloakUserId());
            task.setClaimedAt(Instant.now());
            task.setSlaEscalatedAt(null);
            reviewTaskRepository.save(task);
            log.info("[AUDIT][TASK_FORCE_ASSIGNED] taskId={} fmUserId={} fmSub={}", taskId, fmUserId, fm.getKeycloakUserId());
            String assignBody = """
                    Merhaba,

                    Aşağıdaki inceleme görevi tarafınıza atanmıştır:

                    • Görev ID: %d
                    • Görev tipi: %s

                    Lütfen portaldan görevi açıp gerekli incelemeyi tamamlayınız.

                    İyi çalışmalar,
                    NRS Finance Portal
                    """.formatted(taskId, task.getType() != null ? task.getType().name() : "—");

            notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                    fm.getKeycloakUserId(),
                    "İnceleme görevi size atandı (#%d)".formatted(taskId),
                    assignBody,
                    "REVIEW_TASK_ASSIGNED",
                    "review_task",
                    taskId));
            taskPoolSseService.broadcastFm(Map.of("type", "TASK_FORCE_ASSIGNED", "taskId", taskId));
            return ReviewTaskView.from(getById(taskId), null);
        }

        if ("RESOLVE_ESCALATED_CLEAR".equals(action)) {
            if (task.getType() != ReviewTaskType.SUSPICIOUS_REVIEW && task.getType() != ReviewTaskType.WHALE_REVIEW) {
                throw new IllegalStateException("Bu aksiyon yalnızca eskale FM incelemeleri içindir");
            }
            task.setStatus(ReviewTaskStatus.APPROVED);
            task.setOutcome("Admin doğrudan çözüm — temiz");
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);
            log.info("[AUDIT][TASK_ADMIN_DIRECT_CLEAR] taskId={}", taskId);
            if (task.getAccount() != null && task.getAccount().getUser() != null) {
                notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                        task.getAccount().getUser().getKeycloakUserId(),
                        "İncelemeniz tamamlandı",
                        """
                                Merhaba,

                                Hesabınız üzerindeki inceleme yönetici tarafından sonuçlandırılmıştır.

                                İyi günler dileriz,
                                NRS Finance Portal
                                """,
                        "REVIEW_COMPLETED",
                        "review_task",
                        task.getId()));
            }
            return ReviewTaskView.from(task, null);
        }

        if ("RESOLVE_ESCALATED_FREEZE".equals(action)) {
            if (task.getType() != ReviewTaskType.SUSPICIOUS_REVIEW && task.getType() != ReviewTaskType.WHALE_REVIEW) {
                throw new IllegalStateException("Bu aksiyon yalnızca eskale FM incelemeleri içindir");
            }
            Account account = task.getAccount();
            if (account == null) {
                throw new IllegalStateException("No account on task");
            }
            account.freeze(Instant.now(), reason != null && !reason.isBlank() ? reason : "Admin eskale çözümü — freeze");
            accountRepository.save(account);
            task.setStatus(ReviewTaskStatus.APPROVED);
            task.setOutcome("Admin doğrudan çözüm — freeze");
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);
            log.info("[AUDIT][TASK_ADMIN_DIRECT_FREEZE] taskId={} accountId={}", taskId, account.getId());
            String freezeReason = reason != null && !reason.isBlank() ? reason : "Hesabınız yönetici incelemesi kapsamında dondurulmuştur.";
            String frozenBody = """
                    Merhaba,

                    Güvenlik ve uyumluluk incelemesi kapsamında hesabınız geçici olarak dondurulmuştur.

                    Açıklama: %s

                    Süreç tamamlandığında tarafınıza bilgi verilecektir.

                    NRS Finance Portal
                    """.formatted(freezeReason);
            notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                    account.getUser().getKeycloakUserId(),
                    "Hesabınız güvenlik nedeniyle donduruldu",
                    frozenBody,
                    "ACCOUNT_FROZEN",
                    "account",
                    account.getId()));
            return ReviewTaskView.from(task, null);
        }

        if ("FREEZE".equals(action)) {
            Account account = task.getAccount();
            if (account == null) {
                throw new IllegalStateException("No account on task");
            }

            account.freeze(Instant.now(), reason != null && !reason.isBlank() ? reason : "Admin freeze");
            accountRepository.save(account);

            task.setStatus(ReviewTaskStatus.APPROVED);
            task.setOutcome("Hesap dondur");
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);

            List<ReviewTask> fmTasks = reviewTaskRepository
                    .findByAccount_IdAndStatus(account.getId(), ReviewTaskStatus.FREEZE_REQUESTED);

            for (ReviewTask fmTask : fmTasks) {
                fmTask.setStatus(ReviewTaskStatus.APPROVED);
                fmTask.setOutcome("Freeze talebi admin tarafından onaylandı");
                fmTask.setCompletedAt(Instant.now());
                reviewTaskRepository.save(fmTask);
            }

            String freezeReasonAd = reason != null && !reason.isBlank() ? reason : "İnceleme sonucu hesap donduruldu.";
            String frozenBodyAd = """
                    Merhaba,

                    Hesabınız güvenlik incelemesi sonucunda geçici olarak dondurulmuştur.

                    Açıklama: %s

                    Sorularınız için destek kanallarımızı kullanabilirsiniz.

                    NRS Finance Portal
                    """.formatted(freezeReasonAd);
            notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                    account.getUser().getKeycloakUserId(),
                    "Hesabınız güvenlik nedeniyle donduruldu",
                    frozenBodyAd,
                    "ACCOUNT_FROZEN",
                    "account",
                    account.getId()
            ));

            return ReviewTaskView.from(task, null);
        }

        if ("REJECT_FREEZE".equals(action)) {
            task.setStatus(ReviewTaskStatus.REJECTED);
            task.setOutcome("Reddet");
            task.setCompletedAt(Instant.now());
            reviewTaskRepository.save(task);

            if (task.getAccount() != null) {
                Long accountId = task.getAccount().getId();
                List<ReviewTask> fmTasks = reviewTaskRepository
                        .findByAccount_IdAndStatus(accountId, ReviewTaskStatus.FREEZE_REQUESTED);

                for (ReviewTask fmTask : fmTasks) {
                    fmTask.setStatus(ReviewTaskStatus.REJECTED);
                    fmTask.setOutcome("Freeze talebi admin tarafından reddedildi");
                    fmTask.setCompletedAt(Instant.now());
                    reviewTaskRepository.save(fmTask);
                }
            }

            return ReviewTaskView.from(task, null);
        }

        throw new IllegalArgumentException("Unknown admin action: " + action);
    }

    @Transactional
    public int cleanupDuplicateTasks() {
        AtomicInteger cleaned = new AtomicInteger(0);

        for (ReviewTaskType type : List.of(ReviewTaskType.SUSPICIOUS_REVIEW, ReviewTaskType.WHALE_REVIEW)) {
            List<ReviewTask> openTasks = reviewTaskRepository.findByTypeAndStatusIn(type, FM_ACTIVE_FOR_DUP_CLEANUP);

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

    @Scheduled(fixedDelayString = "PT15M")
    @Transactional(readOnly = true)
    public void sendSlaReminderEmails() {
        Instant now = Instant.now();
        List<ReviewTaskStatus> statuses = List.of(
                ReviewTaskStatus.PENDING,
                ReviewTaskStatus.IN_REVIEW,
                ReviewTaskStatus.CLAIMED);
        Page<ReviewTask> page = reviewTaskRepository
                .findByAssigneeRoleAndStatusInOrderByDueAtAsc("FINANCE_MANAGER", statuses, Pageable.unpaged());
        List<ReviewTask> tasks = page.getContent();
        for (ReviewTask task : tasks) {
            Instant dueAt = task.getDueAt();
            if (dueAt == null) {
                continue;
            }
            long minutesLeft = ChronoUnit.MINUTES.between(now, dueAt);
            if (minutesLeft <= 60 && minutesLeft > 45) {
                String remTitle = "SLA uyarısı — Görev #" + task.getId();
                String remBody = """
                        Merhaba,

                        Üzerinizde veya havuzda bulunan bir inceleme görevinin son tarihine yaklaşılmaktadır.

                        • Görev ID: %d
                        • Tip: %s
                        • Termin: %s

                        Lütfen zamanında aksiyon alınız.

                        NRS Finance Portal
                        """.formatted(
                        task.getId(),
                        task.getType() != null ? task.getType().name() : "—",
                        fmtTr(task.getDueAt()));

                userRepository.findByRole(Role.FINANCE_MANAGER).forEach(fm ->
                        notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                                fm.getKeycloakUserId(),
                                remTitle,
                                remBody,
                                "REVIEW_TASK_REMINDER",
                                "review_task",
                                task.getId()
                        ))
                );
            }
        }
    }

    @Transactional
    public void escalateOverdueTasks() {
        Instant now = Instant.now();
        Instant cutoffCreated = now.minus(24, ChronoUnit.HOURS);
        Instant cutoffClaimed = now.minus(24, ChronoUnit.HOURS);

        List<ReviewTask> unclaimed = reviewTaskRepository.findByAssigneeRoleAndStatusInAndAssignedFmKeycloakIdIsNullAndCreatedAtBefore(
                "FINANCE_MANAGER",
                List.of(ReviewTaskStatus.PENDING, ReviewTaskStatus.IN_REVIEW),
                cutoffCreated);
        for (ReviewTask t : unclaimed) {
            if (t.getType() != ReviewTaskType.SUSPICIOUS_REVIEW && t.getType() != ReviewTaskType.WHALE_REVIEW) {
                continue;
            }
            escalateFmTaskToAdmin(t, "Vaka " + t.getId() + ", 24 saat içinde üstlenilmediği için Admin'e yükseltildi.", null);
        }

        List<ReviewTask> staleClaimed = reviewTaskRepository.findByAssigneeRoleAndStatusAndClaimedAtBefore(
                "FINANCE_MANAGER", ReviewTaskStatus.CLAIMED, cutoffClaimed);
        for (ReviewTask t : staleClaimed) {
            if (t.getType() != ReviewTaskType.SUSPICIOUS_REVIEW && t.getType() != ReviewTaskType.WHALE_REVIEW) {
                continue;
            }
            String fmName = Optional.ofNullable(t.getAssignedFmKeycloakId())
                    .flatMap(userRepository::findByKeycloakUserId)
                    .map(User::getUsername)
                    .orElse(t.getAssignedFmKeycloakId());
            escalateFmTaskToAdmin(t,
                    "Vaka " + t.getId() + ", FM (" + fmName + ") tarafından süresinde çözülmediği için Admin'e yükseltildi.",
                    t.getAssignedFmKeycloakId());
        }
    }

    private void escalateFmTaskToAdmin(ReviewTask t, String auditMessage, String formerFmSub) {
        if (t.getStatus() == ReviewTaskStatus.ESCALATED && "ADMIN".equals(t.getAssigneeRole())) {
            return;
        }
        t.setStatus(ReviewTaskStatus.ESCALATED);
        t.setAssigneeRole("ADMIN");
        t.setAssignedFmKeycloakId(null);
        t.setClaimedAt(null);
        t.setSlaEscalatedAt(Instant.now());
        reviewTaskRepository.save(t);
        log.info("[AUDIT][TASK_ESCALATED] taskId={} detail={} formerFmSub={}", t.getId(), auditMessage, formerFmSub);
        ReviewTask enriched = reviewTaskRepository.findByIdWithAccountAndUser(t.getId()).orElse(t);
        String fmFormerLabel = Optional.ofNullable(formerFmSub)
                .flatMap(userRepository::findByKeycloakUserId)
                .map(u -> nvlText(u.getEmail(), u.getUsername()))
                .orElse("—");
        long delayMinutes = enriched.getCreatedAt() != null
                ? ChronoUnit.MINUTES.between(enriched.getCreatedAt(), Instant.now())
                : -1L;
        String escTitle = "SLA aşımı — İnceleme görevi Admin'e yükseltildi (#%d)".formatted(enriched.getId());
        String escBody = buildEscalationAdminEmailBody(enriched, auditMessage, fmFormerLabel, delayMinutes);
        userRepository.findByRole(Role.ADMIN).forEach(admin ->
                notificationEventKafkaPublisher.publish(new NotificationRequestedEvent(
                        admin.getKeycloakUserId(),
                        escTitle,
                        escBody,
                        "REVIEW_TASK_ESCALATED_CRITICAL",
                        "review_task",
                        t.getId()
                ))
        );
        taskPoolSseService.broadcastAdmin(Map.of(
                "type", "TASK_ESCALATED_ADMIN",
                "taskId", t.getId(),
                "message", auditMessage));
        taskPoolSseService.broadcastFm(Map.of(
                "type", "TASK_REMOVED_FROM_POOL",
                "taskId", t.getId()));
    }

    private static final ZoneId TZ_TR = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter FMT_TR = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(TZ_TR);

    private static String fmtTr(Instant i) {
        return i == null ? "—" : FMT_TR.format(i);
    }

    private static String nvlText(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "—";
    }

    private String buildSuspiciousPoolTaskEmailBody(ReviewTask task, SuspiciousEvent se, User subjectUser) {
        String userEmail = subjectUser != null ? nvlText(subjectUser.getEmail(), "—") : "—";
        String userLabel = subjectUser != null ? nvlText(subjectUser.getUsername(), userEmail) : "—";
        Long userId = subjectUser != null ? subjectUser.getId() : null;
        Long accountId = task.getAccount() != null ? task.getAccount().getId() : null;

        StringBuilder risk = new StringBuilder();
        if (se != null) {
            risk.append("• Şüpheli olay No: ").append(se.getId()).append("\n");
            risk.append("• İşlem ID: ").append(se.getTransactionId() != null ? se.getTransactionId() : "—").append("\n");
            risk.append("• Sınıflandırma: ").append(se.getReason()).append("\n");
            if (se.getAmount() != null) {
                risk.append("• Tutar: ").append(se.getAmount().toPlainString()).append("\n");
            }
            risk.append("• Olay zamanı: ").append(fmtTr(se.getOccurredAt())).append("\n");
        }

        return """
                Merhaba,

                Sistem, şüpheli işlem tespiti sonrası yeni bir inceleme görevi oluşturdu. Görev tüm finans yöneticileri havuzundadır; ilk aksiyonu alan görevi üzerine alır.

                Görev bilgileri:
                • Görev ID: %d
                • Tür: %s
                • Öncelik: %s
                • Oluşturulma: %s
                • Termin: %s

                Kullanıcı:
                • User ID: %s
                • E-posta / kullanıcı adı: %s / %s
                • Hesap ID: %s

                Şüpheli işlem özeti:
                %s
                Lütfen FM panelinden görevi inceleyiniz.

                İyi çalışmalar,
                NRS Finance Portal
                """.formatted(
                task.getId(),
                task.getType() != null ? task.getType().name() : "—",
                task.getPriority() != null ? task.getPriority().name() : "—",
                fmtTr(task.getCreatedAt()),
                fmtTr(task.getDueAt()),
                userId != null ? userId.toString() : "—",
                userEmail,
                userLabel,
                accountId != null ? accountId.toString() : "—",
                risk.length() > 0 ? risk.toString() : "(detay bulunamadı)\n");
    }

    private String buildWhalePoolTaskEmailBody(ReviewTask task, User whaleUser) {
        String email = whaleUser != null ? nvlText(whaleUser.getEmail(), "—") : "—";
        String name = whaleUser != null ? nvlText(whaleUser.getUsername(), email) : "—";
        Long uid = whaleUser != null ? whaleUser.getId() : task.getReferenceId();
        Long accountId = task.getAccount() != null ? task.getAccount().getId() : null;

        return """
                Merhaba,

                Whale uyarısı nedeniyle yüksek öncelikli bir inceleme görevi oluşturuldu. Görev havuzdadır; ilk aksiyonu alan görevi üzerine alır.

                Görev bilgileri:
                • Görev ID: %d
                • Tür: %s
                • Öncelik: %s
                • Oluşturulma: %s
                • Termin: %s

                Kullanıcı:
                • User ID: %s
                • E-posta / kullanıcı adı: %s / %s
                • Hesap ID: %s

                İyi çalışmalar,
                NRS Finance Portal
                """.formatted(
                task.getId(),
                task.getType() != null ? task.getType().name() : "—",
                task.getPriority() != null ? task.getPriority().name() : "—",
                fmtTr(task.getCreatedAt()),
                fmtTr(task.getDueAt()),
                uid != null ? uid.toString() : "—",
                email,
                name,
                accountId != null ? accountId.toString() : "—");
    }

    private String buildEscalationAdminEmailBody(ReviewTask t, String auditMessage, String formerFmLabel, long delayMinutes) {
        User u = t.getAccount() != null && t.getAccount().getUser() != null ? t.getAccount().getUser() : null;
        String userEmail = u != null ? nvlText(u.getEmail(), "—") : "—";
        String userIdStr = u != null ? u.getId().toString() : "—";
        Long accountId = t.getAccount() != null ? t.getAccount().getId() : null;

        return """
                Merhaba,

                Aşağıdaki inceleme görevi SLA süresini aştığı için Admin incelemesine yönlendirilmiştir.

                Özet: %s

                Görev:
                • Görev ID: %d
                • Tür: %s
                • Öncelik: %s
                • Oluşturulma: %s
                • Önceki atanan FM: %s
                • Gecikme (oluşturulmadan bu ana): %s dakika

                Kullanıcı:
                • User ID: %s
                • E-posta: %s
                • Hesap ID: %s

                Lütfen Admin panelinden aksiyon alınız.

                İyi çalışmalar,
                NRS Finance Portal
                """.formatted(
                auditMessage,
                t.getId(),
                t.getType() != null ? t.getType().name() : "—",
                t.getPriority() != null ? t.getPriority().name() : "—",
                fmtTr(t.getCreatedAt()),
                formerFmLabel,
                delayMinutes >= 0 ? Long.toString(delayMinutes) : "—",
                userIdStr,
                userEmail,
                accountId != null ? accountId.toString() : "—");
    }
}
