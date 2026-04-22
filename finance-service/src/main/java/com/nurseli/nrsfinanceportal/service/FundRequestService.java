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
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.FundRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundRequestService {

    private static final String AUTO_APPROVE_NOTE = "AUTO_APPROVED_BY_RULE";

    private final FundRequestRepository fundRequestRepository;
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final CurrentUserResolver currentUserResolver;
    private final TransactionService transactionService;
    private final FundRequestNotificationHelper notificationHelper;
    private final FundRequestRuleProperties ruleProperties;

    @Transactional
    public FundRequest createMyRequest(FundRequestCreateRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();
        Account account = resolveAccount(user, request.getAccountId());

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
                request.getBankAccountIban(),
                request.getReceiptFileUrl(),
                request.getReferenceNo(),
                request.getSourceBankName()
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
        return fundRequestRepository.findByStatusOrderByCreatedAtAsc(FundRequestStatus.PENDING);
    }

    @Transactional
    public FundRequest approve(Long requestId, String reviewNote) {
        User reviewer = currentUserResolver.getOrCreateCurrentUser();

        FundRequest request = fundRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Fund request not found"));

        applyApprovalWithStrictBalanceCheck(request, reviewer.getId(), reviewNote);

        FundRequest saved = fundRequestRepository.save(request);
        notifyAfterCommit(() -> notificationHelper.notifyApproved(saved));
        return saved;
    }

    @Transactional
    public FundRequest reject(Long requestId, String reviewNote) {
        FundRequest request = fundRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Fund request not found"));

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
            if (ruleProperties.isRequireReceiptForDeposit() && !hasText(request.getReceiptFileUrl())) {
                return false;
            }
            if (ruleProperties.isRequireReferenceNoForDeposit() && !hasText(request.getReferenceNo())) {
                return false;
            }
            if (ruleProperties.isRequireIbanForDeposit() && !hasText(request.getBankAccountIban())) {
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