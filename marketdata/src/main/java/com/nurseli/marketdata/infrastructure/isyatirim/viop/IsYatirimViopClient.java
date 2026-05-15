package com.nurseli.marketdata.infrastructure.isyatirim.viop;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Facade over İş Yatırım VIOP HTTP clients (historical series + snapshot).
 */
@Component
@RequiredArgsConstructor
public class IsYatirimViopClient {

    private final IsYatirimViopHistoricalClient historicalClient;
    private final IsYatirimViopSnapshotClient snapshotClient;

    public String fetchHistorical(String contractCode, LocalDateTime from, LocalDateTime to, int periodMinutes) {
        return historicalClient.fetchHistorical(contractCode, from, to, periodMinutes);
    }

    public String fetchSnapshot(String contractCode) {
        return snapshotClient.fetchSnapshot(contractCode);
    }
}
