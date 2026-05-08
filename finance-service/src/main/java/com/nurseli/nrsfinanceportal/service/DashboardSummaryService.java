package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.DashboardSummaryResponse;
import com.nurseli.nrsfinanceportal.common.dto.PerformanceItemDto;
import com.nurseli.nrsfinanceportal.common.dto.PortfolioPerformanceDto;
import com.nurseli.nrsfinanceportal.domain.account.Account;
import com.nurseli.nrsfinanceportal.domain.account.AccountType;
import com.nurseli.nrsfinanceportal.domain.asset.AssetType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.AccountRepository;
import com.nurseli.nrsfinanceportal.repository.BalanceRepository;
import com.nurseli.nrsfinanceportal.repository.TradeRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

    private final WhaleStateCacheService whaleStateCacheService;
    private final PortfolioPerformanceService portfolioPerformanceService;

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final TradeRepository tradeRepository;
    private final UserRepository userRepository;

    public DashboardSummaryResponse getSummary(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return emptySummary();
        }

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
           Portfolio — birleşik (TRADE + MANUAL), /api/portfolio/performance ile aynı mantık
           ====================== */
        PortfolioPerformanceDto performance = portfolioPerformanceService.performanceForUser(user);
        var portfolio = buildPortfolioSummary(performance);

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
                cashTry.add(performance.getTotalCurrentValue());

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
                new DashboardSummaryResponse.PortfolioSummary(
                        BigDecimal.ZERO,
                        Map.of(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        List.of()
                ),
                new DashboardSummaryResponse.ActivitySummary(null, 0),
                BigDecimal.ZERO
        );
    }

    private static DashboardSummaryResponse.PortfolioSummary buildPortfolioSummary(PortfolioPerformanceDto perf) {
        Map<AssetType, BigDecimal> distribution = new EnumMap<>(AssetType.class);
        Map<AssetType, CategoryTotals> byType = new EnumMap<>(AssetType.class);

        for (PerformanceItemDto item : perf.getItems()) {
            AssetType t = AssetType.valueOf(item.getType());
            BigDecimal value = nz(item.getCurrentValue());
            BigDecimal cost = nz(item.getCost());
            distribution.merge(t, value, BigDecimal::add);
            byType.computeIfAbsent(t, k -> new CategoryTotals()).add(cost, value);
        }

        List<DashboardSummaryResponse.PortfolioCategoryBreakdown> categories = new ArrayList<>();
        for (Map.Entry<AssetType, CategoryTotals> e : byType.entrySet()) {
            CategoryTotals acc = e.getValue();
            BigDecimal pnl = acc.valueTry.subtract(acc.costTry);
            BigDecimal pnlPct = acc.costTry.signum() == 0
                    ? BigDecimal.ZERO
                    : pnl.divide(acc.costTry, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            categories.add(new DashboardSummaryResponse.PortfolioCategoryBreakdown(
                    e.getKey(),
                    acc.valueTry,
                    acc.costTry,
                    pnl,
                    pnlPct
            ));
        }
        categories.sort(Comparator.comparing(DashboardSummaryResponse.PortfolioCategoryBreakdown::valueTry).reversed());

        return new DashboardSummaryResponse.PortfolioSummary(
                nz(perf.getTotalCurrentValue()),
                distribution,
                nz(perf.getTotalCost()),
                nz(perf.getTotalPnl()),
                nz(perf.getTotalPnlPct()),
                List.copyOf(categories)
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static final class CategoryTotals {
        private BigDecimal costTry = BigDecimal.ZERO;
        private BigDecimal valueTry = BigDecimal.ZERO;

        void add(BigDecimal cost, BigDecimal value) {
            this.costTry = this.costTry.add(cost);
            this.valueTry = this.valueTry.add(value);
        }
    }
}