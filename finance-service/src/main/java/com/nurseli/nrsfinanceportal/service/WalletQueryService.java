package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.WalletDepositInstructionsDto;
import com.nurseli.nrsfinanceportal.common.dto.WalletSummaryDto;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.fund.FundRequest;
import com.nurseli.nrsfinanceportal.domain.fund.FundRequestStatus;
import com.nurseli.nrsfinanceportal.domain.fund.FundRequestType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.FundRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletQueryService {

    private final CurrentUserResolver currentUserResolver;
    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final FundRequestRepository fundRequestRepository;

    @Transactional(readOnly = true)
    public WalletSummaryDto mySummary() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        Account account = accountRepository.findByUserAndType(user, AccountType.CASH)
                .orElseThrow(() -> new IllegalStateException("Cash account not found"));
        BigDecimal current = balanceRepository.findByAccount(account)
                .orElseThrow(() -> new IllegalStateException("Balance not found"))
                .getAmount();

        List<FundRequest> requests = fundRequestRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        BigDecimal pendingDeposit = requests.stream()
                .filter(r -> r.getStatus() == FundRequestStatus.PENDING && r.getType() == FundRequestType.DEPOSIT)
                .map(FundRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal pendingWithdrawal = requests.stream()
                .filter(r -> r.getStatus() == FundRequestStatus.PENDING && r.getType() == FundRequestType.WITHDRAWAL)
                .map(FundRequest::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = current.subtract(pendingWithdrawal);
        if (available.signum() < 0) {
            available = BigDecimal.ZERO;
        }
        return new WalletSummaryDto(
                account.getId(),
                current,
                available,
                pendingDeposit,
                pendingWithdrawal
        );
    }

    @Transactional(readOnly = true)
    public WalletDepositInstructionsDto myDepositInstructions() {
        User user = currentUserResolver.getOrCreateCurrentUser();
        String userCode = "U" + user.getId();
        String systemIbanId = "SYS-" + userCode;
        String virtualIban = createVirtualIban(user.getId());
        return new WalletDepositInstructionsDto(
                virtualIban,
                "NRS Finance Portal",
                "NRS Virtual Bank",
                userCode,
                systemIbanId
        );
    }

    private String createVirtualIban(Long userId) {
        String suffix = String.format("%024d", userId);
        return "TR99" + suffix;
    }
}
