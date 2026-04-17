package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.FundRequestCreateRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FundRequestService {

    private final FundRequestRepository fundRequestRepository;
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final CurrentUserResolver currentUserResolver;
    private final TransactionService transactionService;
    private final FundRequestNotificationHelper notificationHelper;

    @Transactional
    public FundRequest createMyRequest(FundRequestCreateRequest request) {
        User user = currentUserResolver.getOrCreateCurrentUser();

        Account account = resolveAccount(user, request.getAccountId());

        if (account.isFrozen()) {
            throw new IllegalStateException("Account is frozen");
        }

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

        request.approve(reviewer.getId(), reviewNote);
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