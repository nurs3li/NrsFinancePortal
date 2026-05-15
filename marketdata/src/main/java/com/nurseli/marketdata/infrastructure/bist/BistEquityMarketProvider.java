package com.nurseli.marketdata.infrastructure.bist;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * BIST günlük fiyat sağlayıcıları (HisseTekil, Yahoo, …) için ortak sözleşme.
 * <p>Not: {@code application.equity.bist.BistEquityProvider} (intraday / snapshot) ile karıştırılmamalıdır.</p>
 */
public interface BistEquityMarketProvider {

    String sourceName();

    boolean supports(String symbol);

    BistProviderResult<List<BistEquityDailyPrice>> fetchHistory(String symbol, LocalDate from, LocalDate to);

    BistProviderResult<Optional<BistEquityDailyPrice>> fetchLatest(String symbol);
}
