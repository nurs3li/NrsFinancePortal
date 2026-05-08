package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.PortfolioPerformanceDto;
import com.nurseli.nrsfinanceportal.common.dto.PortfolioSnapshotMetricsDto;
import com.nurseli.nrsfinanceportal.common.dto.RiskMonitorUserDetailResponse;
import com.nurseli.nrsfinanceportal.common.dto.RiskMonitorUserResponse;
import com.nurseli.nrsfinanceportal.common.dto.WhaleTimelineResponse;
import com.nurseli.nrsfinanceportal.domain.transaction.Transaction;
import com.nurseli.nrsfinanceportal.domain.user.Role;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.domain.whale.WhaleHistory;
import com.nurseli.nrsfinanceportal.repository.SuspiciousEventRepository;
import com.nurseli.nrsfinanceportal.repository.TransactionRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import com.nurseli.nrsfinanceportal.repository.WhaleHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WhaleTimelineService {

    private final WhaleHistoryRepository repository;
    private final UserRepository userRepository;
    private final PortfolioPerformanceService portfolioPerformanceService;
    private final TransactionRepository transactionRepository;
    private final SuspiciousEventRepository suspiciousEventRepository;

    public List<WhaleTimelineResponse> getTimeline(Long userId) {
        return repository
                .findByUserIdOrderByTriggeredAtDesc(userId)
                .stream()
                .map(h -> new WhaleTimelineResponse(
                        h.getId(),
                        h.getUserId(),
                        h.getWhaleLevel().name(),
                        h.getImpactScore(),
                        h.getReason(),
                        h.getDailyVolume(),
                        h.getHourlyTransactionCount(),
                        h.getMaxSingleTransaction(),
                        h.getPattern(),
                        h.getBehavior(),
                        h.getRisk(),
                        h.getTriggeredAt(),
                        h.getCreatedAt()
                ))
                .toList();
    }

    public List<RiskMonitorUserResponse> getRiskMonitorUsers() {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.USER)
                .toList();
        return users.stream()
                .map(this::toRiskMonitorUser)
                .sorted(Comparator
                        .comparing(RiskMonitorUserResponse::userId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public RiskMonitorUserDetailResponse getRiskMonitorUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getRole() != Role.USER) {
            throw new IllegalArgumentException("Risk monitor supports USER role only");
        }

        PortfolioPerformanceDto performance = portfolioPerformanceService.performanceForUser(user);
        List<RiskMonitorUserDetailResponse.PortfolioSlice> slices = buildPortfolioSlices(performance);

        List<WhaleTimelineResponse> whaleEvents = getTimeline(userId).stream().limit(10).toList();
        RiskMonitorUserDetailResponse.WhaleLatest latestWhale = whaleEvents.isEmpty()
                ? null
                : new RiskMonitorUserDetailResponse.WhaleLatest(
                whaleEvents.get(0).whaleLevel(),
                whaleEvents.get(0).behavior(),
                whaleEvents.get(0).pattern(),
                whaleEvents.get(0).risk(),
                whaleEvents.get(0).impactScore(),
                whaleEvents.get(0).triggeredAt()
        );

        var suspiciousRecent = suspiciousEventRepository.findByUserIdOrderByOccurredAtDesc(userId).stream()
                .filter(e -> e.getTransactionId() != null)
                .limit(10)
                .toList();
        Map<Long, Integer> suspiciousByTx = suspiciousRecent.stream()
                .collect(Collectors.toMap(
                        e -> e.getTransactionId(),
                        e -> {
                            if ("HIGH_AMOUNT".equalsIgnoreCase(e.getReason())) return 85;
                            if ("HIGH_FREQUENCY".equalsIgnoreCase(e.getReason())) return 70;
                            return 50;
                        },
                        Math::max
                ));
        Map<Long, String> suspiciousReasonByTx = suspiciousRecent.stream()
                .collect(Collectors.toMap(
                        e -> e.getTransactionId(),
                        e -> e.getReason(),
                        (left, right) -> left
                ));

        List<RiskMonitorUserDetailResponse.TransactionRiskView> txViews = suspiciousRecent.stream()
                .map(e -> transactionRepository.findById(e.getTransactionId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(tx -> toTransactionRiskView(tx, suspiciousByTx, suspiciousReasonByTx))
                .toList();

        return new RiskMonitorUserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                performance,
                slices,
                latestWhale,
                whaleEvents,
                txViews
        );
    }

    private RiskMonitorUserResponse toRiskMonitorUser(User user) {
        WhaleHistory latestWhale = repository.findTopByUserIdOrderByTriggeredAtDesc(user.getId()).orElse(null);
        long eventCount = repository.countByUserId(user.getId());
        PortfolioSnapshotMetricsDto metrics = portfolioPerformanceService.computeSnapshotMetricsForUser(user);
        BigDecimal portfolioTotal = metrics == null || metrics.combinedValueTry() == null
                ? BigDecimal.ZERO
                : metrics.combinedValueTry();
        return new RiskMonitorUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                portfolioTotal,
                latestWhale != null && latestWhale.getWhaleLevel() != null ? latestWhale.getWhaleLevel().name() : null,
                latestWhale != null ? latestWhale.getRisk() : null,
                latestWhale != null ? latestWhale.getReason() : null,
                latestWhale != null ? latestWhale.getTriggeredAt() : null,
                eventCount
        );
    }

    private List<RiskMonitorUserDetailResponse.PortfolioSlice> buildPortfolioSlices(PortfolioPerformanceDto performance) {
        if (performance == null || performance.getItems() == null || performance.getItems().isEmpty()) {
            return List.of();
        }
        Map<String, BigDecimal> values = performance.getItems().stream()
                .collect(Collectors.groupingBy(
                        item -> item.getType() == null ? "OTHER" : item.getType(),
                        Collectors.mapping(item -> item.getCurrentValue() == null ? BigDecimal.ZERO : item.getCurrentValue(),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));
        BigDecimal total = performance.getTotalCurrentValue() == null ? BigDecimal.ZERO : performance.getTotalCurrentValue();
        if (total.signum() == 0) {
            return List.of();
        }
        return values.entrySet().stream()
                .map(e -> new RiskMonitorUserDetailResponse.PortfolioSlice(
                        e.getKey(),
                        e.getValue(),
                        e.getValue()
                                .multiply(new BigDecimal("100"))
                                .divide(total, 4, java.math.RoundingMode.HALF_UP)
                ))
                .sorted(Comparator.comparing(RiskMonitorUserDetailResponse.PortfolioSlice::valueTry, Comparator.reverseOrder()))
                .toList();
    }

    private RiskMonitorUserDetailResponse.TransactionRiskView toTransactionRiskView(
            Transaction tx,
            Map<Long, Integer> suspiciousByTx,
            Map<Long, String> suspiciousReasonByTx
    ) {
        Integer riskScore = suspiciousByTx.getOrDefault(tx.getId(), 0);
        String riskReason = suspiciousReasonByTx.get(tx.getId());
        return new RiskMonitorUserDetailResponse.TransactionRiskView(
                tx.getId(),
                tx.getType() == null ? "-" : tx.getType().name(),
                tx.getAmount(),
                tx.getBalanceAfter(),
                tx.getCreatedAt(),
                riskScore,
                riskReason
        );
    }
}
