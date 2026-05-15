package com.nurseli.marketdata.infrastructure.bist;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * İş Yatırım HisseTekil (ve ileride diğer kaynaklar) için normalize günlük satır.
 */
public record BistEquityDailyPrice(
        String symbol,
        LocalDate date,
        BigDecimal adjustedClose,
        BigDecimal adjustedAverage,
        BigDecimal adjustedLow,
        BigDecimal adjustedHigh,
        BigDecimal adjustedVolume,
        BigDecimal rawClose,
        BigDecimal rawAverage,
        BigDecimal rawLow,
        BigDecimal rawHigh,
        BigDecimal rawVolume,
        BigDecimal usdTry,
        BigDecimal bist100Value,
        BigDecimal usdPrice,
        BigDecimal indexBasedPrice,
        BigDecimal usdVolume,
        BigDecimal capital,
        BigDecimal marketCapTry,
        BigDecimal marketCapUsd,
        BigDecimal freeFloatMarketCapTry,
        BigDecimal freeFloatMarketCapUsd,
        BigDecimal dollarBasedLow,
        BigDecimal dollarBasedHigh,
        BigDecimal dollarBasedAverage,
        BistProviderSource source,
        BistDataQuality dataQuality,
        Instant lastUpdated
) {}
