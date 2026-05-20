package com.nurseli.marketdata.application.inflation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class InflationPpiIngestService {

    private final InflationIndexIngestService inflationIndexIngestService;

    public InflationPpiSyncResult syncRange(LocalDate fromInclusive, LocalDate toInclusive) {
        InflationPpiSyncResult r = inflationIndexIngestService.syncPpiRange(fromInclusive, toInclusive);
        return r;
    }

    public record InflationPpiSyncResult(int monthsProcessed, int rowsUpserted, LocalDate from, LocalDate to, String status) {}
}
