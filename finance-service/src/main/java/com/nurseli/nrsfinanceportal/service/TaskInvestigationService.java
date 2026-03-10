package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.TaskInvestigationContext;
import com.nurseli.nrsfinanceportal.common.dto.TaskInvestigationContext.*;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.suspicious.SuspiciousEvent;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTask;
import com.nurseli.nrsfinanceportal.domain.task.ReviewTaskType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.ReviewTaskRepository;
import com.nurseli.nrsfinanceportal.repository.SuspiciousEventRepository;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import com.nurseli.nrsfinanceportal.repository.WhaleHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskInvestigationService {

    private final ReviewTaskRepository reviewTaskRepository;
    private final SuspiciousEventRepository suspiciousEventRepository;
    private final WhaleHistoryRepository whaleHistoryRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public TaskInvestigationContext getContext(Long taskId) {
        ReviewTask task = reviewTaskRepository.findByIdWithAccountAndUser(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));

        Account account = task.getAccount();
        if (account == null) {
            throw new IllegalStateException("Task has no linked account");
        }

        User user = account.getUser();

        UserSummary userSummary = new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.isWhale(),
                user.getWhaleLevel() != null ? user.getWhaleLevel().name() : null
        );

        AccountSummary accountSummary = new AccountSummary(
                account.getId(),
                account.getType().name(),
                account.getStatus().name(),
                account.getFrozenAt(),
                account.getFrozenReason()
        );

        SuspiciousEventSummary suspiciousEvent = null;
        if (task.getType() == ReviewTaskType.SUSPICIOUS_REVIEW && task.getReferenceId() != null) {
            suspiciousEvent = suspiciousEventRepository.findById(task.getReferenceId())
                    .map(se -> new SuspiciousEventSummary(
                            se.getId(),
                            se.getReason(),
                            se.getAmount(),
                            se.getCountInWindow(),
                            se.getThresholdAmount(),
                            se.getThresholdCount(),
                            se.getOccurredAt()
                    ))
                    .orElse(null);
        }

        List<WhaleEntry> whaleHistory = whaleHistoryRepository
                .findByUserIdOrderByTriggeredAtDesc(user.getId())
                .stream()
                .limit(10)
                .map(h -> new WhaleEntry(
                        h.getId(),
                        h.getWhaleLevel().name(),
                        h.getImpactScore(),
                        h.getReason(),
                        h.getTriggeredAt()
                ))
                .toList();

        List<TxEntry> recentTransactions = transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(account.getId(), Pageable.ofSize(20))
                .map(tx -> new TxEntry(
                        tx.getId(),
                        tx.getType().name(),
                        tx.getAmount(),
                        tx.getBalanceAfter(),
                        tx.getCreatedAt()
                ))
                .toList();

        return new TaskInvestigationContext(
                userSummary, accountSummary, suspiciousEvent, whaleHistory, recentTransactions
        );
    }
}
