package com.nurseli.marketdata.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "market.bist")
@Data
public class BistProperties {

    private boolean enabled = false;
    private boolean schedulerEnabled = false;

    /**
     * Uygulama açılışında {@code market_price_history} içinde IS_YATIRIM günlük satırı yoksa
     * sınırlı sembol / tarih aralığı ile bir kez backfill dener (Docker için varsayılan açık).
     */
    private boolean seedOnStart = false;

    /** Boş DB seed: HisseTekil penceresi (~2 yıl takvim günü; iş günü mum sayısı daha az olabilir). */
    private int seedLookbackDays = 730;
    /** Boş / sığ DB: katalogdan en fazla bu kadar sembol (katalog 20 ise 20 = tamamı). */
    private int seedMaxSymbols = 20;

    /** Zamanlayıcı: son N günü tekrar çeker (tam yıllık backfill değil). */
    private int schedulerIncrementalLookbackDays = 30;

    /**
     * DB'deki son gün bu kadar günden eskiyse okuma / seed sırasında HisseTekil ile kuyruk doldurulur
     * (hafta sonu için 3 gün varsayılan).
     */
    private int staleTailDays = 3;

    /** Europe/Istanbul — HisseTekil günlük yenileme */
    private String schedulerZone = "Europe/Istanbul";

    private String dailyIngestCron = "0 20 7 * * MON-FRI";

    /** Borsa kapanışı sonrası ikinci günlük çekim (İstanbul). */
    private String dailyIngestCronClose = "0 30 18 * * MON-FRI";
    private String primaryProvider = "IS_YATIRIM";
    private String fallbackProvider = "YAHOO";
    private boolean fallbackToDb = true;
    private int defaultLookbackYears = 2;
    private List<String> symbols = new ArrayList<>();

    /** İş Yatırım HisseTekil ve benzeri uçlar için kök URL */
    private String isyatirimBaseUrl = "https://www.isyatirim.com.tr";
    private int connectTimeoutMs = 5000;
    /** HisseTekil uzun aralık (≈730 gün) tek yanıtta büyük JSON olabilir. */
    private int readTimeoutMs = 45000;

    /**
     * HisseTekil tek çağrıda tüm günlükleri dönmeyebilir; bu kadar takvim günü penceresiyle
     * sırayla çekilir (örn. 730 gün → birden fazla HTTP isteği).
     */
    private int historyFetchChunkDays = 90;

    /** Parça çekimleri arası bekleme (ms); 0 = yok. */
    private int delayMsBetweenHistoryChunks = 150;
}
