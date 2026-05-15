package com.nurseli.marketdata.application;

import com.nurseli.marketdata.api.dto.CpiTrMacroResponse;
import com.nurseli.marketdata.config.EvdsProperties;
import com.nurseli.marketdata.config.EvdsSeriesLogicalNames;
import com.nurseli.marketdata.infrastructure.evds.EvdsDebtClient;
import com.nurseli.marketdata.infrastructure.evds.EvdsSeriesPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EvdsCpiTrService {

    private final EvdsProperties evdsProperties;
    private final EvdsDebtClient evdsDebtClient;

    /**
     * Yapılandırılmış EVDS endeks serisinden son ayın seviyesi ile MoM/YoY yüzdeleri.
     */
    public Optional<CpiTrMacroResponse> latestCpiTr() {
        if (!evdsProperties.isEnabled()) {
            return Optional.empty();
        }
        String series = evdsProperties.getSeriesCode(EvdsSeriesLogicalNames.CPI_TR_INDEX);
        if (series == null || series.isBlank()) {
            return Optional.empty();
        }
        String trimmed = series.trim();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(24).minusDays(15);
        List<EvdsSeriesPoint> points = evdsDebtClient.fetchSeriesAscending(trimmed, start, end);
        return CpiTrComputation.fromAscendingPoints(points, trimmed);
    }
}
