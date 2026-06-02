package com.nurseli.nrsfinanceportal.application.dashboard;

import com.nurseli.nrsfinanceportal.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * finance-service günlük portfolio snapshot job'u — portföyü olan kullanıcılar için eksik günlük snapshot kaydı oluşturur.
 */
@Slf4j
@RequiredArgsConstructor
@Component

public class PortfolioDailySnapshotJob {

    private final UserRepository userRepository;
    private final PortfolioSnapshotRecorder portfolioSnapshotRecorder;

    /** Her gün 23:30 İstanbul — portföyü olan kullanıcılar için günlük snapshot (yoksa). */
    /**
     * {@code runDailySnapshots} — Her gün 23:30 İstanbul'da pozisyonu olan tüm kullanıcılar için günlük snapshot yoksa kaydeder.
     */
    @Scheduled(cron = "0 30 23 * * *", zone = "Europe/Istanbul")
    public void runDailySnapshots() {
        List<Long> ids = userRepository.findIdsWithPortfolioPositions();
        log.info("[PORTFOLIO_SNAPSHOT][DAILY] users={}", ids.size());
        for (Long userId : ids) {
            try {
                portfolioSnapshotRecorder.recordDailyIfMissingForUser(userId);
            } catch (Exception e) {
                log.warn("[PORTFOLIO_SNAPSHOT][DAILY] user={} err={}", userId, e.getMessage());
            }
        }
    }
}
