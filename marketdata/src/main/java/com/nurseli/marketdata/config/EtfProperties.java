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
}