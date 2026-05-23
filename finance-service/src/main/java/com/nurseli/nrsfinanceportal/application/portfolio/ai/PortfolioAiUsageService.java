package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.nurseli.nrsfinanceportal.api.dto.portfolioai.PortfolioAiUsageResponse;
import com.nurseli.nrsfinanceportal.api.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.api.response.ApiErrorCode;
import com.nurseli.nrsfinanceportal.domain.portfolio.ai.PortfolioAiAnalysisEntity;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.PortfolioAiAnalysisRepository;
import com.nurseli.nrsfinanceportal.application.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * finance-service portfolio AI kullanım servisi — günlük analiz kotası takibi ve limit kontrolü yapar.
 */
@RequiredArgsConstructor
@Service

public class PortfolioAiUsageService {

    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final CurrentUserResolver currentUserResolver;
    private final PortfolioAiAnalysisRepository repository;
    private final PortfolioAiModuleProperties moduleProperties;

    /**
     * {@code usageForCurrentUser} — Kullanıcının bugünkü analiz sayısı ve kalan günlük kotayı döner.
     */
    @Transactional(readOnly = true)
    public PortfolioAiUsageResponse usageForCurrentUser() {
        Long userId = currentUserResolver.getCurrentUserId();
        int dailyLimit = moduleProperties.getDailyLimit();
        Instant dayStart = startOfToday();
        long usedToday = countSuccessfulToday(userId, dayStart);
        int remaining = dailyLimit <= 0
                ? Integer.MAX_VALUE
                : Math.max(0, dailyLimit - (int) usedToday);
        PortfolioAiAnalysisEntity latest = repository.findFirstByUser_IdOrderByCreatedAtDesc(userId).orElse(null);
        return new PortfolioAiUsageResponse(
                dailyLimit,
                (int) usedToday,
                remaining,
                latest != null ? latest.getCreatedAt() : null,
                latest != null ? latest.getPortfolioScore() : null,
                latest != null ? latest.getRiskScore() : null
        );
    }

    /**
     * {@code assertDailyQuotaAvailable} — Günlük limit dolmuşsa ApiBusinessException fırlatır.
     */
    @Transactional(readOnly = true)
    public void assertDailyQuotaAvailable() {
        int dailyLimit = moduleProperties.getDailyLimit();
        if (dailyLimit <= 0) {
            return;
        }
        Long userId = currentUserResolver.getCurrentUserId();
        Instant dayStart = startOfToday();
        long usedToday = countSuccessfulToday(userId, dayStart);
        if (usedToday >= dailyLimit) {
            throw new ApiBusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ApiErrorCode.AI_DAILY_LIMIT_REACHED,
                    "Bugünkü AI analiz hakkınızı kullandınız."
            );
        }
    }

    private long countSuccessfulToday(Long userId, Instant dayStart) {
        return repository.countByUser_IdAndCreatedAtGreaterThanEqualAndModelNot(
                userId,
                dayStart,
                PortfolioAiModels.FALLBACK_MODEL
        );
    }

    private Instant startOfToday() {
        return LocalDate.now(TZ).atStartOfDay(TZ).toInstant();
    }
}
