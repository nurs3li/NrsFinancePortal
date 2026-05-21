package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.market.eurobonds.evds")
@Data
public class EurobondEvdsProperties {

    /** Makro panel (TP_EBOND* EVDS). Tekil ISIN modülü: {@link Instruments#enabled}. */
    private boolean enabled = true;

    private int defaultLookbackYears = 5;

    private Backfill backfill = new Backfill();

    private LatestRefresh latestRefresh = new LatestRefresh();

    private Series series = new Series();

    private Instruments instruments = new Instruments();

    @Data
    public static class Backfill {
        private boolean startupEnabled = false;
        private LocalDate from = LocalDate.now().minusYears(5);
    }

    @Data
    public static class Instruments {
        private boolean enabled = false;
        private boolean startupBackfillEnabled = false;
        private int defaultLookbackYears = 5;
        private boolean seedFallbackEnabled = true;
        private List<Instrument> list = new ArrayList<>();
    }

    @Data
    public static class Instrument {
        private String isin;
        private String name;
        private String issuer = "Hazine";
        private String currency = "USD";
        private BigDecimal couponPct;
        private String maturityDate;
        /** EVDS fiyat serisi (ör. TP_…); US ISIN için genelde boş kalır. */
        private String dirtyPriceSeries;
        private String yieldSeries;
        private BigDecimal dirtyPriceScale = BigDecimal.ONE;
        private BigDecimal yieldScale = BigDecimal.ONE;
        /** 100 nominal üzerinden gösterge fiyat (broker orta). */
        private BigDecimal referenceCleanPrice;
        /** Yıllık getiri % (broker). */
        private BigDecimal referenceYieldPct;
        /** Tipik minimum işlem tutarı (USD). */
        private Long minLotUsd = 200_000L;
    }

    @Data
    public static class LatestRefresh {
        private boolean enabled = false;
        private long fixedDelayMs = 86_400_000L;
    }

    @Data
    public static class Series {
        private String bookValue = "TP_EBONDYAZDEG_ST";
        private String marketValue = "TP_EBONDPIYDEG_ST";
        private String total = "TP_EBONDVADE_C8_ST";
        private String originalMaturityShort = "TP_EBONDVADE_C1_ST";
        private String originalMaturityLong = "TP_EBONDVADE_C2_ST";
        private String remainingMaturityShort = "TP_EBONDVADE_C3_ST";
        private String remainingMaturityLong = "TP_EBONDVADE_C4_ST";
        private String currencyUsd = "TP_EBONDVADE_C5_ST";
        private String currencyEur = "TP_EBONDVADE_C6_ST";
        private String currencyJpy = "TP_EBONDVADE_C7_ST";
    }
}
