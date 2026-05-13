package com.nurseli.nrsfinanceportal.service;

import com.nurseli.nrsfinanceportal.common.dto.PortfolioSnapshotMetricsDto;
import com.nurseli.nrsfinanceportal.domain.portfolio.PortfolioValueSnapshot;
import com.nurseli.nrsfinanceportal.domain.portfolio.SnapshotTriggerType;
import com.nurseli.nrsfinanceportal.domain.user.User;
import com.nurseli.nrsfinanceportal.repository.PortfolioValueSnapshotRepository;
import com.nurseli.nrsfinanceportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotRecorder {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    private final UserRepository userRepository;
    private final PortfolioValueSnapshotRepository snapshotRepository;
    private final PortfolioPerformanceService portfolioPerformanceService;

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
