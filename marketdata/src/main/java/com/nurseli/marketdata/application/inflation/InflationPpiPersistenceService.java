package com.nurseli.marketdata.application.inflation;

import com.nurseli.marketdata.domain.inflation.InflationIndicatorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InflationPpiPersistenceService {

    private final InflationIndexPersistenceService inflationIndexPersistenceService;

    public void upsertPpiMonth(String seriesCode, int baseYear, InflationMonthMetrics metrics) {
        inflationIndexPersistenceService.upsertMonth(InflationIndicatorType.PPI, seriesCode, baseYear, metrics);
    }
}
