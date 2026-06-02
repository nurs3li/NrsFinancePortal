package com.nurseli.nrsfinanceportal.application.portfolio.ai;

import com.nurseli.nrsfinanceportal.api.exception.ApiBusinessException;
import com.nurseli.nrsfinanceportal.infrastructure.persistence.PortfolioAiAnalysisRepository;
import com.nurseli.nrsfinanceportal.application.user.CurrentUserResolver;
import com.nurseli.nrsfinanceportal.config.PortfolioAiModuleProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioAiUsageServiceTest {

    @Mock
    private CurrentUserResolver currentUserResolver;
    @Mock
    private PortfolioAiAnalysisRepository repository;
    @Mock
    private PortfolioAiModuleProperties moduleProperties;

    @InjectMocks
    private PortfolioAiUsageService usageService;

    @Test
    void skipsQuotaWhenDailyLimitDisabled() {
        when(moduleProperties.getDailyLimit()).thenReturn(0);
        usageService.assertDailyQuotaAvailable();
    }

    @Test
    void blocksWhenDailyLimitReached() {
        when(currentUserResolver.getCurrentUserId()).thenReturn(42L);
        when(moduleProperties.getDailyLimit()).thenReturn(1);
        when(repository.countByUser_IdAndCreatedAtGreaterThanEqualAndModelNot(
                eq(42L), any(), eq(PortfolioAiModels.FALLBACK_MODEL))).thenReturn(1L);

        assertThatThrownBy(() -> usageService.assertDailyQuotaAvailable())
                .isInstanceOf(ApiBusinessException.class);
    }
}
