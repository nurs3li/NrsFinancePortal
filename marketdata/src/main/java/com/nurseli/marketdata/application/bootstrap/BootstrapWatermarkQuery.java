package com.nurseli.marketdata.application.bootstrap;

import com.nurseli.marketdata.application.inflation.InflationIndexQueryService;
import com.nurseli.marketdata.config.InflationCpiProperties;
import com.nurseli.marketdata.config.InflationPpiProperties;
import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import com.nurseli.marketdata.domain.inflation.InflationIndexMonthlyEntity;
import com.nurseli.marketdata.domain.metal.PreciousMetalUsdCatalog;
import com.nurseli.marketdata.infrastructure.persistence.DepositRateObservationRepository;
import com.nurseli.marketdata.infrastructure.persistence.EurobondWeeklyObservationRepository;
import com.nurseli.marketdata.infrastructure.persistence.InflationIndexMonthlyRepository;
import com.nurseli.marketdata.infrastructure.persistence.MarketPriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BootstrapWatermarkQuery {

    private final InflationIndexMonthlyRepository inflationRepository;
    private final InflationIndexQueryService inflationIndexQueryService;
    private final InflationCpiProperties inflationCpiProperties;
    private final InflationPpiProperties inflationPpiProperties;
    private final DepositRateObservationRepository depositRateObservationRepository;
    private final EurobondWeeklyObservationRepository eurobondWeeklyObservationRepository;
    private final MarketPriceHistoryRepository marketPriceHistoryRepository;

    public Optional<LocalDate> inflationMaxObservationMonth() {
        Optional<LocalDate> max = Optional.empty();
        if (inflationCpiProperties.isPersistEnabled()) {
            max = later(max, maxMonth(InflationIndicatorType.CPI));
        }
        if (inflationPpiProperties.isPersistEnabled()) {
            max = later(max, maxMonth(InflationIndicatorType.PPI));
        }
        return max;
    }

    public Optional<LocalDate> depositMaxObservationDate() {
        return depositRateObservationRepository.findMaxObservationDate();
    }

    public Optional<LocalDate> eurobondMaxObservationDate() {
        return eurobondWeeklyObservationRepository.findMaxObservationDate();
    }

    public long eurobondRowCount() {
        return eurobondWeeklyObservationRepository.count();
    }

    public Optional<LocalDate> metalMaxObservationDate(String canonicalSymbol) {
        return marketPriceHistoryRepository
                .findTopBySymbolAndSourceOrderByTimestampDesc(canonicalSymbol, PreciousMetalUsdCatalog.SOURCE)
                .map(m -> m.getTimestamp().toLocalDate());
    }

    private Optional<LocalDate> maxMonth(InflationIndicatorType type) {
        String code = inflationIndexQueryService.resolveSeriesCode(type);
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Optional<InflationIndexMonthlyEntity> row =
                inflationRepository.findFirstByIndicatorTypeAndSeriesCodeOrderByObservationMonthDesc(type, code.trim());
        return row.map(InflationIndexMonthlyEntity::getObservationMonth);
    }

    private static Optional<LocalDate> later(Optional<LocalDate> a, Optional<LocalDate> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a.get().isAfter(b.get()) ? a : b;
    }
}
