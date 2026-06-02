package com.nurseli.nrsfinanceportal.application.market;

import com.nurseli.nrsfinanceportal.api.dto.MarketDashboardResponse;
import com.nurseli.nrsfinanceportal.api.dto.MarketOverviewResponse;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDashboardServiceTest {

    @Mock
    private MarketOverviewService overviewService;

    @Mock
    private MarketDataClient marketDataClient;

    private MarketDashboardService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdownDashboardRefreshExecutor();
        }
    }

    @Test
    void buildDashboardReusesFreshCachedResponse() {
        service = new MarketDashboardService(overviewService, marketDataClient);
        when(overviewService.getOverview()).thenReturn(new MarketOverviewResponse(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                LocalDateTime.of(2026, 5, 25, 21, 0)));
        when(marketDataClient.getBistBatchHistoryMapped(anyString(), any(), any())).thenReturn(Map.of());

        MarketDashboardResponse first = service.buildDashboard();
        MarketDashboardResponse second = service.buildDashboard();

        assertThat(second).isSameAs(first);
        verify(overviewService, times(1)).getOverview();
        verify(marketDataClient, times(1)).getBistBatchHistoryMapped(anyString(), any(), any());
    }
}
