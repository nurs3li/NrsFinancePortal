package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.etf")
public class EtfProperties {
    /** FinHub free plan: US ETF sembolleri (SPY, QQQ, VOO, ...) */
    private List<String> symbols = new ArrayList<>();

    /**
     * Günlük kısa aralık backfill ({@code FundMarketScheduler}); quote job kaçırsa eksik günler dolar.
     * Varsayılan: her gün 06:45 Europe/Istanbul.
     */
    private String historyIncrementalCron = "0 45 6 * * *";

    /** FundPriceIngestService.ingestHistory için pencere (gün). */
    private int historyIncrementalDays = 30;

    /**
     * Finnhub ücretsiz planda günlük mum (candle) dönmeyen ETF'ler — doğrudan Yahoo history.
     * Örnek: IWM, GLD. Boş bırakılırsa çalışma anında öğrenilen semboller kullanılır.
     */
    private List<String> historyYahooOnlySymbols = new ArrayList<>(List.of("IWM", "GLD"));

    /**
     * true: ETF geçmiş (candle) yalnızca Yahoo — Finnhub free planda SPY/QQQ/IWM/… mum vermez.
     * Günlük anlık fiyat ({@code ingestForDate}) hâlâ Finnhub quote kullanabilir.
     */
    private boolean historyUseYahooOnly = true;

    /** DB son tarih bu kadar gün içindeyse startup/cron history atlanır. */
    private int historySkipIfFreshWithinDays = 3;
}