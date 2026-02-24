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
           2️⃣ Demo account & balance – yoksa boş özet (ADMIN/FINANCE_MANAGER vb.)
           ====================== */
        var demoAccountOpt = accountRepository.findByUserIdAndType(userId, AccountType.DEMO);
        if (demoAccountOpt.isEmpty()) {
            return emptySummary();
        }
        Account demoAccount = demoAccountOpt.get();

        var balanceOpt = balanceRepository.findByAccount(demoAccount);
        if (balanceOpt.isEmpty()) {
            return emptySummary();
        }
        BigDecimal cashTry = balanceOpt.get().getAmount();

        /* ======================
           1️⃣ Whale (Redis)
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
           3️⃣ Portfolio (Market priced)
           ====================== */
        var portfolioSummary =
                portfolioAggregationService.aggregate(demoAccount.getUser());

        var portfolio =
                new DashboardSummaryResponse.PortfolioSummary(
                        portfolioSummary.totalTry(),
                        portfolioSummary.distribution()
                );

        /* ======================
           4️⃣ Activity
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
           5️⃣ Net Worth
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