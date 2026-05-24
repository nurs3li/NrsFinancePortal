package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.market.viop")
@Data
public class MarketViopProperties {

    private boolean enabled = true;
    private String provider = "IS_YATIRIM";
    private int delayMinutes = 15;
    private int defaultPeriodMinutes = 60;
    private int cacheTtlSeconds = 900;
    private String timezone = "Europe/Istanbul";
    private IsYatirim isyatirim = new IsYatirim();
    private Whitelist whitelist = new Whitelist();
    private Scheduler scheduler = new Scheduler();

    @Data
    public static class IsYatirim {
        private String baseUrl = "https://www.isyatirim.com.tr";
        private String historyPath = "/_Layouts/15/IsYatirim.Website/Common/ChartData.aspx/IndexHistoricalAll";
        private String snapshotPath = "/_layouts/15/Isyatirim.Website/Common/Data.aspx/OneEndeks";
        private String referer = "https://www.isyatirim.com.tr/tr-tr/analiz/Sayfalar/viop.aspx";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 10000;
        private String userAgent =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";
    }

    @Data
    public static class Whitelist {
        private List<IndexEntry> index = new ArrayList<>();
        private List<IndexEntry> fx = new ArrayList<>();
        /** Kıymetli maden VİOP (İş Yatırım IndexHistoricalAll / OneEndeks ile aynı akış). */
        private List<IndexEntry> preciousMetal = new ArrayList<>();
        /** Pay (hisse) vadeli VİOP — aynı İş Yatırım uçları. */
        private List<IndexEntry> equity = new ArrayList<>();
    }

    @Data
    public static class Scheduler {
        /** Master: true iken VIOP zamanlayıcı bean'i yüklenir (@Scheduled metodları). */
        private boolean enabled = false;
        /** Snapshot periyodu (fixed delay, ms). */
        private long snapshotFixedDelayMs = 3_600_000L;
        /** History periyodu (fixed delay, ms). */
        private long historyFixedDelayMs = 3_600_000L;
        /**
         * İlk snapshot job'undan önce bekleme (ms). History ile aynı anda patlamasın diye küçük tutulabilir;
         * 0 = hemen (Spring varsayılanı gibi).
         */
        private long snapshotInitialDelayMs = 120_000L;
        /**
         * İlk history job'undan önce bekleme (ms). Snapshot'dan sonra çalışsın diye varsayılan ~33 dk ofset.
         */
        private long historyInitialDelayMs = 1_980_000L;
        /** Snapshot otomasyonu (master açıkken). */
        private boolean snapshotEnabled = true;
        /** History otomasyonu; varsayılan kapalı — admin/manuel refresh ile doldurma önerilir. */
        private boolean historyEnabled = false;
        /** History çekiminde kullanılacak mum aralığı (dakika). */
        private int periodMinutes = 60;
        /** Geçmiş penceresi (gün). */
        private int historyLookbackDays = 7;
        private int historyChunkDays = 7;
        /**
         * {@code price-at} isteğinde DB boşsa İş Yatırım'dan geriye dönük çekilecek gün sayısı.
         */
        private int priceAtBackfillDays = 60;
    }

    @Data
    public static class IndexEntry {
        private String underlying;
        private String displayName;
        private String contractName;
        private String contractCode;
        private int maturityMonth = 12;
        private int maturityYear = 2026;
        private String segment = "INDEX_FUTURES";
        private String assetClass = "INDEX";
        private String chartType = "PRICE_SERIES";
        private boolean enabled = true;
    }
}
