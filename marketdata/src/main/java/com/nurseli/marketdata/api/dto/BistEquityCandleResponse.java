package com.nurseli.marketdata.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BistEquityCandleResponse(
        String symbol,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        BigDecimal adjustedClose,
        BigDecimal rawClose,
        BigDecimal usdTry,
        BigDecimal bist100Value,
        BigDecimal marketCapTry,
        String source,
        String dataQuality) {

    public static BistEquityCandleResponse fromHistory(BistEquityHistoryResponse h) {
        return new BistEquityCandleResponse(
                h.symbol(),
                h.date(),
                h.open(),
                h.high(),
                h.low(),
                h.close(),
                h.volume(),
                h.adjustedClose(),
                h.rawClose(),
                h.usdTry(),
                h.bist100Value(),
                h.marketCapTry(),
                h.source(),
                h.dataQuality());
    }
}
