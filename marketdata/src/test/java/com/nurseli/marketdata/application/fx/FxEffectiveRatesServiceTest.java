package com.nurseli.marketdata.application.fx;

import com.nurseli.marketdata.api.dto.fx.FxEffectiveRatesResponseDto;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FxEffectiveRatesServiceTest {

    @Mock
    private EvdsDebtClient evdsDebtClient;

    @Mock
    private EvdsProperties evdsProperties;

    private FxEffectiveRatesService newService() {
        FxEffectiveRatesService svc = new FxEffectiveRatesService(evdsDebtClient, evdsProperties);
        ReflectionTestUtils.setField(svc, "lookbackDays", 60);
        return svc;
    }

    @Test
    void load_whenEvdsDisabled_returnsEmptyRatesWithNote() {
        when(evdsProperties.isEnabled()).thenReturn(false);
        FxEffectiveRatesResponseDto r = newService().load();
        assertThat(r.rates()).isEmpty();
        assertThat(r.source()).isEqualTo("EVDS");
        assertThat(r.frequency()).isEqualTo("DAILY");
        assertThat(r.notes()).anyMatch(s -> s.contains("devre dışı"));
    }

    @Test
    void load_mergesUsdSpreads() {
        when(evdsProperties.isEnabled()).thenReturn(true);
        LocalDate d = LocalDate.of(2026, 5, 15);
        when(evdsDebtClient.fetchSeriesAscending(anyString(), any(), any()))
                .thenAnswer(inv -> {
                    String code = inv.getArgument(0);
                    var t = d.atStartOfDay();
                    return switch (code) {
                        case "TP_DK_USD_A_YTL" -> List.of(new EvdsSeriesPoint(t, new BigDecimal("40.00")));
                        case "TP_DK_USD_S_YTL" -> List.of(new EvdsSeriesPoint(t, new BigDecimal("40.10")));
                        case "TP_DK_USD_A_EF_YTL" -> List.of(new EvdsSeriesPoint(t, new BigDecimal("39.95")));
                        case "TP_DK_USD_S_EF_YTL" -> List.of(new EvdsSeriesPoint(t, new BigDecimal("40.25")));
                        default -> List.of();
                    };
                });

        FxEffectiveRatesResponseDto r = newService().load();
        var usd = r.rates().stream().filter(x -> "USD".equals(x.currency()) && "2026-05-15".equals(x.date())).findFirst();
        assertThat(usd).isPresent();
        assertThat(usd.get().fxBuying()).isEqualTo(40.0);
        assertThat(usd.get().fxSelling()).isEqualTo(40.1);
        assertThat(usd.get().fxSpread()).isEqualTo(0.1);
        assertThat(usd.get().cashSpread()).isEqualTo(0.3);
        assertThat(usd.get().cashVsFxBuyingDiff()).isEqualTo(-0.05);
        assertThat(usd.get().cashVsFxSellingDiff()).isEqualTo(0.15);
    }
}
