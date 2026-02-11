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

        /* ======================
           2️⃣ Cash (TRY Balance)
           ====================== */
        Account demoAccount =
                accountRepository.findByUserIdAndType(userId, AccountType.DEMO)
                        .orElseThrow(() ->
                                new IllegalStateException("Demo account not found"));

        BigDecimal cashTry =
                balanceRepository.findByAccount(demoAccount)
                        .orElseThrow(() ->
                                new IllegalStateException("Balance not found"))
                        .getAmount();

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
}
