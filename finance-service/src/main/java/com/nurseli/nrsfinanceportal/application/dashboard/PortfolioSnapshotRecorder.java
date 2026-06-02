package com.nurseli.nrsfinanceportal.application.dashboard;

import com.nurseli.nrsfinanceportal.api.dto.PortfolioSnapshotMetricsDto;
import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioValueSnapshot;
import com.nurseli.nrsfinanceportal.domain.portfolio.SnapshotTriggerType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.PortfolioValueSnapshotRepository;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * finance-service portfolio snapshot kaydedici — anlık portfolio metriklerini snapshot tablosuna yazar.
 */
@Slf4j
@RequiredArgsConstructor
@Service

public class PortfolioSnapshotRecorder {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    private final UserRepository userRepository;
    private final PortfolioValueSnapshotRepository snapshotRepository;
    private final PortfolioPerformanceService portfolioPerformanceService;

    /**
     * {@code record} — Kullanıcı için güncel portfolio metriklerini belirtilen trigger türüyle snapshot olarak kaydeder.
     */
    @Transactional
    public void record(Long userId, SnapshotTriggerType trigger) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[PORTFOLIO_SNAPSHOT] user not found id={}", userId);
            return;
        }
        PortfolioSnapshotMetricsDto m = portfolioPerformanceService.computeSnapshotMetricsForUser(user);
        PortfolioValueSnapshot row = PortfolioValueSnapshot.create(
                user,
                Instant.now(),
                trigger,
                m.portfolioValueTry(),
                m.portfolioCostTry(),
                m.portfolioPnlTry()
        );
        snapshotRepository.save(row);
        log.info("[PORTFOLIO_SNAPSHOT] saved user={} trigger={}", userId, trigger);
    }

    /**
     * {@code recordDailyIfMissingForUser} — Bugün için DAILY trigger snapshot yoksa yeni günlük snapshot oluşturur.
     */
    @Transactional
    public void recordDailyIfMissingForUser(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        ZonedDateTime startZ = ZonedDateTime.now(IST).toLocalDate().atStartOfDay(IST);
        Instant start = startZ.toInstant();
        Instant end = startZ.plusDays(1).toInstant();
        if (snapshotRepository.existsByUserAndTriggerBetween(userId, SnapshotTriggerType.DAILY, start, end)) {
            return;
        }
        record(userId, SnapshotTriggerType.DAILY);
    }
}
