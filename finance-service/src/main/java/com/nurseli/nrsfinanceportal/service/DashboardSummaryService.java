package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.DashboardSummaryResponse;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioAggregationService;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

    private final WhaleStateCacheService whaleStateCacheService;
    private final PortfolioAggregationService portfolioAggregationService;

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final TradeRepository tradeRepository;

    public DashboardSummaryResponse getSummary(Long userId) {

        /* ======================
           Demo account & balance – yoksa boş özet (ADMIN/FINANCE_MANAGER vb.)
           ====================== */
        List<Account> cashAccounts = accountRepository.findByUserIdAndType(userId, AccountType.CASH);
        if (cashAccounts.isEmpty()) {
            return emptySummary();
        }
        Account cashAccount = cashAccounts.get(0);

        var balanceOpt = balanceRepository.findByAccount(cashAccount);
        if (balanceOpt.isEmpty()) {
            return emptySummary();
        }
        BigDecimal cashTry = balanceOpt.get().getAmount();

        /* ======================
           Whale (Redis)
           ====================== */
        var whaleState = whaleStateCacheService.getLastWhaleState(userId);

        DashboardSummaryResponse.WhaleSummary whale =
                whaleState == null
                        ? null
                        : new DashboardSummaryResponse.WhaleSummary(
                        whaleState.level(),
                        whaleState.impactScore(),
                        whaleState.triggeredAt()
                );

        var cash =
                new DashboardSummaryResponse.CashSummary(cashTry);

        /* ======================
           Portfolio (Market priced)
           ====================== */
        var portfolioSummary =
                portfolioAggregationService.aggregate(cashAccount.getUser());

        var portfolio =
                new DashboardSummaryResponse.PortfolioSummary(
                        portfolioSummary.totalTry(),
                        portfolioSummary.distribution()
                );

        /* ======================
          Activity
           ====================== */
        Instant todayStart =
                LocalDate.now()
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC);

        int todayTradeCount =
                tradeRepository.countTradesSince(userId, todayStart);

        Instant lastTradeAt =
                tradeRepository.findLastTradeTime(userId);

        var activity =
                new DashboardSummaryResponse.ActivitySummary(
                        lastTradeAt,
                        todayTradeCount
                );

        /* ======================
           Net Worth
           ====================== */
        BigDecimal netWorthTry =
                cashTry.add(portfolioSummary.totalTry());

        return new DashboardSummaryResponse(
                whale,
                cash,
                portfolio,
                activity,
                netWorthTry
        );
    }

    /**
     * Demo hesabı veya balance olmayan kullanıcılar için (ADMIN, FINANCE_MANAGER vb.).
     */
    private static DashboardSummaryResponse emptySummary() {
        return new DashboardSummaryResponse(
                null,
                new DashboardSummaryResponse.CashSummary(BigDecimal.ZERO),
                new DashboardSummaryResponse.PortfolioSummary(BigDecimal.ZERO, Map.of()),
                new DashboardSummaryResponse.ActivitySummary(null, 0),
                BigDecimal.ZERO
        );
    }
}