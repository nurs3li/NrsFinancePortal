package com.nurseli.nrsfinanceportal.service.viopbond;

import com.nurseli.nrsfinanceportal.common.dto.HistoricalPriceMatchType;
import com.nurseli.nrsfinanceportal.common.dto.PositionHistoricalPriceResolveDto;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.DebtHistoryRow;
import com.nurseli.nrsfinanceportal.infrastructure.client.market.MarketDataClient.ViopPriceAtRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionHistoricalPriceResolverServiceTest {

    @Mock
    private MarketDataClient marketDataClient;

    @InjectMocks
    private PositionHistoricalPriceResolverService service;

    @Test
    void resolveViopMapsExactMatch() {
        LocalDate d = LocalDate.of(2026, 1, 15);
        when(marketDataClient.getViopPriceAt(eq("F_USDTRY1226"), eq(d)))
                .thenReturn(Optional.of(new ViopPriceAtRow(
                        "F_USDTRY1226",
                        d.atStartOfDay(),
                        d.atTime(18, 0),
                        new BigDecimal("35.12"),
                        "EXACT",
                        "VIOP_DB",
                        "OK")));

        PositionHistoricalPriceResolveDto r = service.resolveViop("USDTRY1226", d);
        assertThat(r.getMatchType()).isEqualTo(HistoricalPriceMatchType.EXACT);
        assertThat(r.getPrice()).isEqualByComparingTo("35.12");
        assertThat(r.getMatchedDate()).isEqualTo(d);
    }

    @Test
    void resolveBondUsesPreviousCloseWhenExactMissing() {
        LocalDate requested = LocalDate.of(2026, 3, 10);
        LocalDate prev = LocalDate.of(2026, 3, 7);
        when(marketDataClient.getDebtHistory(eq("TRTEST"), anyInt()))
                .thenReturn(List.of(
                        new DebtHistoryRow("TRTEST", new BigDecimal("98.5"), null, prev.atTime(12, 0)),
                        new DebtHistoryRow("TRTEST", new BigDecimal("99.0"), null, LocalDate.of(2026, 3, 12).atTime(12, 0))));

        PositionHistoricalPriceResolveDto r = service.resolveBond("TRTEST", requested);
        assertThat(r.getMatchType()).isEqualTo(HistoricalPriceMatchType.PREVIOUS_CLOSE);
        assertThat(r.getPrice()).isEqualByComparingTo("98.5");
        assertThat(r.getMatchedDate()).isEqualTo(prev);
    }

    @Test
    void resolveBondNotFoundWhenEmptyHistory() {
        when(marketDataClient.getDebtHistory(any(), anyInt())).thenReturn(List.of());
        PositionHistoricalPriceResolveDto r = service.resolveBond("TRTEST", LocalDate.of(2026, 1, 1));
        assertThat(r.getMatchType()).isEqualTo(HistoricalPriceMatchType.NOT_FOUND);
        assertThat(r.getPrice()).isNull();
    }
}
