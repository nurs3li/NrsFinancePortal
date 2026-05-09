package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.FundRequestCreateRequest;
import com.nurseli.nrsfinanceportal.config.FundRequestRuleProperties;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.balance.Balance;
import com.nurseli.nrsfinanceportal.domain.fund.FundRequest;
import com.nurseli.nrsfinanceportal.domain.fund.FundRequestStatus;
import com.nurseli.nrsfinanceportal.domain.fund.FundRequestType;
import com.nurseli.nrsfinanceportal.domain.transaction.TransactionType;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.integration.sse.TaskPoolSseService;
import com.nurseli.nrsfinanceportal.repository.FundRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundRequestService {

    private static final String AUTO_APPROVE_NOTE = "AUTO_APPROVED_BY_RULE";
    private static final java.util.regex.Pattern TR_IBAN_PATTERN = java.util.regex.Pattern.compile("^TR\\d{24}$");

    private final FundRequestRepository fundRequestRepository;
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final CurrentUserResolver currentUserResolver;
    private final TransactionService transactionService;
    private final FundRequestNotificationHelper notificationHelper;
    private final FundRequestRuleProperties ruleProperties;
    private final TaskPoolSseService taskPoolSseService;

    @Transactional
    public FundRequest createMyRequest(FundRequestCreateRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        Account account = resolveAccount(user, request.getAccountId());
        validateRequestByType(request);

        if (account.isFrozen()) {
            throw new IllegalStateException("Account is frozen");
        }

        // İlk guard (hızlı fail): withdrawal bakiyesi
        if (request.getType() == FundRequestType.WITHDRAWAL) {
            Balance balance = balanceRepository.findByAccount(account)
                    .orElseThrow(() -> new IllegalStateException("Balance not found"));

            if (balance.getAmount().compareTo(request.getAmount()) < 0) {
                throw new IllegalStateException("Insufficient balance for withdrawal request");
            }
        }

        FundRequest fundRequest = FundRequest.create(
                user,
                account,
                request.getType(),
                request.getAmount(),
                request.getCurrency(),
                request.getRequestNote(),
                resolveLegacyBankIban(request),
                request.getReceiptFileUrl(),
                request.getReceiptFileId(),
                request.getSourceBankName(),
                request.getDepositIban(),
                request.getSystemIbanId(),
                request.getDestinationIban(),
                request.getDestinationAccountHolder(),
                request.getDestinationBankName()
        );

        FundRequest saved = fundRequestRepository.save(fundRequest);

        if (isAutoApprovalCandidate(request)) {
            boolean autoApproved = tryAutoApprove(saved);
            if (autoApproved) {
                notifyAfterCommit(() -> notificationHelper.notifyApproved(saved));
                return saved;
            }
            // Auto uygun görünse bile lock anında yetersiz bakiye/frozen gibi durum çıktıysa FM'e düşsün
            log.info("[FUND_REQUEST] auto-approve fallback to manual review. requestId={}", saved.getId());
        }

        // Manual review
        notifyAfterCommit(() -> notificationHelper.notifyCreatedForFinanceManagers(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FundRequest> myRequests() {
        Long userId = currentUserResolver.getCurrentUserId();
        return fundRequestRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<FundRequest> pendingRequests() {
        User fm = currentUserResolver.getOrCreateCurrentUser();
        if (fm.getRole() != Role.FINANCE_MANAGER) {
            throw new IllegalStateException("Only FINANCE_MANAGER can list pending fund requests");
        }
        String fmSub = fm.getKeycloakUserId();
        List<FundRequest> unclaimed = fundRequestRepository
                .findByStatusAndAssignedFmKeycloakIdIsNullOrderByCreatedAtAsc(FundRequestStatus.PENDING);
        List<FundRequest> mine = fundRequestRepository
                .findByStatusAndAssignedFmKeycloakIdOrderByCreatedAtAsc(FundRequestStatus.PENDING, fmSub);
        if (mine.isEmpty()) return unclaimed;
        java.util.LinkedHashMap<Long, FundRequest> map = new java.util.LinkedHashMap<>();
        for (FundRequest r : mine) map.put(r.getId(), r);
        for (FundRequest r : unclaimed) map.putIfAbsent(r.getId(), r);
        return map.values().stream().toList();
    }

    @Transactional
    public FundRequest claimPending(Long requestId) {
        User fm = currentUserResolver.getOrCreateCurrentUser();
        if (fm.getRole() != Role.FINANCE_MANAGER) {
            throw new IllegalStateException("Only FINANCE_MANAGER can claim pending requests");
        }
        String fmSub = fm.getKeycloakUserId();
        int updated = fundRequestRepository.tryClaimByFm(
                requestId,
                fmSub,
                Instant.now(),
                FundRequestStatus.PENDING
        );
        FundRequest current = fundRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Fund request not found"));
        if (updated == 0) {
            if (current.getStatus() != FundRequestStatus.PENDING) {
                throw new IllegalStateException("Fund request already finalized");
            }
            if (current.getAssignedFmKeycloakId() != null && !current.getAssignedFmKeycloakId().equals(fmSub)) {
                throw new IllegalStateException("Fund request is already claimed by another FM");
            }
        } else {
            notifyAfterCommit(
                    () -> taskPoolSseService.broadcastFm(
                            Map.ofEntries(
                                    Map.entry("type", (Object) "FUND_REQUEST_CLAIMED"),
                                    Map.entry("fundRequestId", requestId)
                            )
                    )
            );
        }
        return current;
    }

    @Transactional
    public FundRequest approve(Long requestId, String reviewNote) {
        User reviewer = currentUserResolver.getOrCreateCurrentUser();

        FundRequest request = fundRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Fund request not found"));

        if (reviewer.getRole() == Role.FINANCE_MANAGER) {
            request.assertClaimedBy(reviewer.getKeycloakUserId());
        }

        applyApprovalWithStrictBalanceCheck(request, reviewer.getId(), reviewNote);

        FundRequest saved = fundRequestRepository.save(request);
        notifyAfterCommit(() -> notificationHelper.notifyApproved(saved));
        return saved;
    }

    @Transactional
    public FundRequest reject(Long requestId, String reviewNote) {
        User reviewer = currentUserResolver.getOrCreateCurrentUser();
        FundRequest request = fundRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Fund request not found"));

        if (reviewer.getRole() == Role.FINANCE_MANAGER) {
            request.assertClaimedBy(reviewer.getKeycloakUserId());
        }

        request.reject(reviewNote);
        FundRequest saved = fundRequestRepository.save(request);

        notifyAfterCommit(() -> notificationHelper.notifyRejected(saved));
        return saved;
    }

    private boolean tryAutoApprove(FundRequest request) {
        Account account = request.getAccount();
        if (account.isFrozen()) {
            return false;
        }

        Balance balance = balanceRepository.findByAccountForUpdate(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        if (request.getType() == FundRequestType.DEPOSIT) {
            BigDecimal balanceAfter = balance.increase(request.getAmount());
            balanceRepository.save(balance);

            transactionService.recordForUser(
                    account,
                    request.getUser(),
                    request.getAmount(),
                    TransactionType.DEPOSIT,
                    balanceAfter
            );

            request.approve(null, AUTO_APPROVE_NOTE);
            fundRequestRepository.save(request);
            return true;
        }

        // WITHDRAWAL auto onay: lock sonrası tekrar kontrol
        if (balance.getAmount().compareTo(request.getAmount()) < 0) {
            return false;
        }

        BigDecimal balanceAfter = balance.decrease(request.getAmount());
        balanceRepository.save(balance);

        transactionService.recordForUser(
                account,
                request.getUser(),
                request.getAmount(),
                TransactionType.WITHDRAW,
                balanceAfter
        );

        request.approve(null, AUTO_APPROVE_NOTE);
        fundRequestRepository.save(request);
        return true;
    }

    private void applyApprovalWithStrictBalanceCheck(FundRequest request, Long approverId, String reviewNote) {
        Account account = request.getAccount();

        if (account.isFrozen()) {
            throw new IllegalStateException("Account is frozen");
        }

        Balance balance = balanceRepository.findByAccountForUpdate(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"));

        BigDecimal balanceAfter;
        TransactionType txType;

        if (request.getType() == FundRequestType.DEPOSIT) {
            balanceAfter = balance.increase(request.getAmount());
            txType = TransactionType.DEPOSIT;
        } else {
            if (balance.getAmount().compareTo(request.getAmount()) < 0) {
                throw new IllegalStateException("Insufficient balance for withdrawal approval");
            }
            balanceAfter = balance.decrease(request.getAmount());
            txType = TransactionType.WITHDRAW;
        }

        balanceRepository.save(balance);

        transactionService.recordForUser(
                account,
                request.getUser(),
                request.getAmount(),
                txType,
                balanceAfter
        );

        request.approve(approverId, reviewNote);
    }

    private boolean isAutoApprovalCandidate(FundRequestCreateRequest request) {
        if (!ruleProperties.isAutoApprovalEnabled()) {
            return false;
        }

        String currency = request.getCurrency();
        String effectiveCurrency = (currency == null || currency.isBlank()) ? "TRY" : currency.trim().toUpperCase(Locale.ROOT);
        if (!"TRY".equals(effectiveCurrency)) {
            return false;
        }

        BigDecimal amount = request.getAmount();
        if (amount == null || amount.signum() <= 0) {
            return false;
        }

        if (request.getType() == FundRequestType.DEPOSIT) {
            if (amount.compareTo(ruleProperties.getDepositAutoApproveLimitTry()) > 0) {
                return false;
            }
            if (ruleProperties.isRequireIbanForDeposit() && !hasText(resolveLegacyBankIban(request))) {
                return false;
            }
            return true;
        }

        if (request.getType() == FundRequestType.WITHDRAWAL) {
            return amount.compareTo(ruleProperties.getWithdrawalAutoApproveLimitTry()) <= 0;
        }

        return false;
    }

    private Account resolveAccount(User user, Long accountId) {
        if (accountId != null) {
            Account acc = accountRepository.findById(accountId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));

            if (!acc.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Account does not belong to current user");
            }
            return acc;
        }

        return accountRepository.findByUserAndType(user, AccountType.CASH)
                .orElseThrow(() -> new IllegalStateException("Cash account not found"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void validateRequestByType(FundRequestCreateRequest request) {
        if (request.getType() == FundRequestType.DEPOSIT) {
            // Dekont: otomatik onay veya FM incelemesi — tüm yatırım talepleri için zorunlu (config ile kapatılamaz).
            if (!hasText(request.getReceiptFileUrl()) && !hasText(request.getReceiptFileId())) {
                throw new IllegalArgumentException("Deposit request requires receipt file");
            }
            if (!hasText(request.getDepositIban()) && !hasText(request.getBankAccountIban())) {
                throw new IllegalArgumentException("Deposit request requires system iban");
            }
            return;
        }
        if (request.getType() == FundRequestType.WITHDRAWAL) {
            if (!hasText(request.getDestinationIban())) {
                throw new IllegalArgumentException("Withdrawal request requires destination iban");
            }
            String iban = request.getDestinationIban().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            if (!TR_IBAN_PATTERN.matcher(iban).matches()) {
                throw new IllegalArgumentException("Invalid iban. It must start with TR and contain 24 digits");
            }
            if (!hasText(request.getDestinationAccountHolder())) {
                throw new IllegalArgumentException("Withdrawal request requires destination account holder");
            }
            if (!hasText(request.getDestinationBankName())) {
                throw new IllegalArgumentException("Withdrawal request requires destination bank name");
            }
        }
    }

    private String resolveLegacyBankIban(FundRequestCreateRequest request) {
        if (request.getType() == FundRequestType.DEPOSIT) {
            return hasText(request.getDepositIban()) ? request.getDepositIban() : request.getBankAccountIban();
        }
        return request.getDestinationIban();
    }

    private void notifyAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}